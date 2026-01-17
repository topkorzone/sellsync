package com.sellsync.infra.erp.ecount;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.erp.client.ErpClient;
import com.sellsync.api.domain.erp.dto.*;
import com.sellsync.infra.erp.ecount.auth.EcountApiException;
import com.sellsync.infra.erp.ecount.auth.EcountSessionService;
import com.sellsync.infra.erp.ecount.dto.EcountCredentials;
import com.sellsync.api.domain.credential.service.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class EcountClient implements ErpClient {

    private static final String ERP_CODE = "ECOUNT";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EcountSessionService sessionService;
    private final CredentialService credentialService;

    @Override
    public String getErpCode() {
        return ERP_CODE;
    }

    @Override
    public ErpPostingResult postSalesDocument(UUID tenantId, ErpSalesDocumentRequest request) {
        EcountCredentials creds = getCredentials(tenantId);
        String sessionId = sessionService.getSessionId(tenantId, creds);
        // SESSION_ID를 URL 쿼리 파라미터로 전달 (이카운트 API 요구사항)
        String url = String.format("https://oapi%s.ecount.com/OAPI/V2/Sale/SaveSale?SESSION_ID=%s", 
                creds.getZone(), sessionId);

        try {
            Map<String, Object> body = buildSalesDocumentPayload(request);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (isSuccess(root)) {
                String docNo = extractDocumentNo(root);
                log.info("[Ecount] Sales document posted: {}", docNo);
                
                return ErpPostingResult.builder()
                        .success(true)
                        .documentNo(docNo)
                        .rawResponse(response.getBody())
                        .build();
            } else {
                String errorMsg = extractErrorMessage(root);
                log.warn("[Ecount] Sales document failed: {}", errorMsg);
                
                // 세션 만료 시 재시도
                if (isSessionExpired(root)) {
                    sessionService.invalidateSession(tenantId);
                    return postSalesDocument(tenantId, request); // 재귀 호출 (1회)
                }
                
                return ErpPostingResult.builder()
                        .success(false)
                        .errorCode(extractErrorCode(root))
                        .errorMessage(errorMsg)
                        .rawResponse(response.getBody())
                        .build();
            }

        } catch (Exception e) {
            log.error("[Ecount] Failed to post sales document", e);
            return ErpPostingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public List<ErpItemDto> getItems(UUID tenantId, ErpItemSearchRequest request) {
        EcountCredentials creds = getCredentials(tenantId);
        String sessionId = sessionService.getSessionId(tenantId, creds);
        // SESSION_ID를 URL 쿼리 파라미터로 전달
        String url = String.format("https://oapi%s.ecount.com/OAPI/V2/InventoryBasic/GetBasicProductsList?SESSION_ID=%s", 
                creds.getZone(), sessionId);

        try {
            // Body에 페이징 파라미터 추가 (API가 지원하는 경우)
            Map<String, Object> body = new HashMap<>();
            
            // 페이징 파라미터가 있으면 추가
            if (request != null) {
                if (request.getPage() != null) {
                    body.put("PAGE_NO", request.getPage());
                }
                if (request.getSize() != null) {
                    body.put("PER_PAGE_CNT", request.getSize());
                }
                if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                    body.put("PROD_CD", request.getKeyword());
                    body.put("PROD_DES", request.getKeyword());
                }
                if (request.getCategoryCode() != null && !request.getCategoryCode().isEmpty()) {
                    body.put("CLASS_CD", request.getCategoryCode());
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            log.info("[Ecount] GetItems Request: URL={}, Body={}", 
                    url.replace(sessionId, "***SESSION***"), 
                    body.isEmpty() ? "{}" : "page=" + body.getOrDefault("PAGE_NO", "?") + 
                            ", size=" + body.getOrDefault("PER_PAGE_CNT", "?"));

            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            String responseBody = response.getBody();
            
            if (responseBody == null) {
                throw new EcountApiException("Empty response from Ecount API");
            }
            
            log.debug("[Ecount] GetItems Response: Status={}, Body={}", 
                    response.getStatusCode(), 
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
            
            JsonNode root = objectMapper.readTree(responseBody);

            if (isSuccess(root)) {
                List<ErpItemDto> items = parseItems(root.path("Data").path("Result"));
                log.info("[Ecount] GetItems Success: {} items fetched (page: {}, size: {})", 
                        items.size(), 
                        body.getOrDefault("PAGE_NO", "all"), 
                        body.getOrDefault("PER_PAGE_CNT", "all"));
                return items;
            } else {
                if (isSessionExpired(root)) {
                    log.warn("[Ecount] Session expired, retrying...");
                    sessionService.invalidateSession(tenantId);
                    return getItems(tenantId, request);
                }
                String errorMsg = extractErrorMessage(root);
                log.error("[Ecount] GetItems Failed: {}", errorMsg);
                throw new EcountApiException(errorMsg);
            }

        } catch (EcountApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Ecount] Failed to get items", e);
            throw new EcountApiException("Failed to get items", e);
        }
    }

    @Override
    public List<ErpCustomerDto> getCustomers(UUID tenantId, ErpCustomerSearchRequest request) {
        EcountCredentials creds = getCredentials(tenantId);
        String sessionId = sessionService.getSessionId(tenantId, creds);
        // SESSION_ID를 URL 쿼리 파라미터로 전달
        String url = String.format("https://oapi%s.ecount.com/OAPI/V2/Account/GetListCustomer?SESSION_ID=%s", 
                creds.getZone(), sessionId);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("CUST_CD", request.getKeyword() != null ? request.getKeyword() : "");
            body.put("CUST_DES", request.getKeyword() != null ? request.getKeyword() : "");
            body.put("PAGE_NO", request.getPage() != null ? request.getPage() : 1);
            body.put("PER_PAGE_CNT", request.getSize() != null ? request.getSize() : 100);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (isSuccess(root)) {
                return parseCustomers(root.path("Data").path("Datas"));
            } else {
                if (isSessionExpired(root)) {
                    sessionService.invalidateSession(tenantId);
                    return getCustomers(tenantId, request);
                }
                throw new EcountApiException(extractErrorMessage(root));
            }

        } catch (EcountApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Ecount] Failed to get customers", e);
            throw new EcountApiException("Failed to get customers", e);
        }
    }

    @Override
    public boolean testConnection(UUID tenantId) {
        try {
            EcountCredentials creds = getCredentials(tenantId);
            String sessionId = sessionService.getSessionId(tenantId, creds);
            return sessionId != null && !sessionId.isEmpty();
        } catch (Exception e) {
            log.warn("[Ecount] Connection test failed for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * 전표입력 API (SaveSale)
     * - 여러 전표를 한 번에 입력
     * - UPLOAD_SER_NO가 같은 항목끼리 한 전표로 묶임
     */
    public ErpPostingResult saveSaleForms(UUID tenantId, com.sellsync.infra.erp.ecount.dto.EcountSaleFormRequest request) {
        EcountCredentials creds = getCredentials(tenantId);
        String sessionId = sessionService.getSessionId(tenantId, creds);
        String url = String.format("https://oapi%s.ecount.com/OAPI/V2/Sale/SaveSale?SESSION_ID=%s", 
                creds.getZone(), sessionId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<com.sellsync.infra.erp.ecount.dto.EcountSaleFormRequest> httpRequest = 
                    new HttpEntity<>(request, headers);

            log.info("[Ecount] SaveSale Request: URL={}, Forms={}", 
                    url.replace(sessionId, "***SESSION***"), 
                    request.getSaleList().size());

            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (isSuccess(root)) {
                log.info("[Ecount] SaveSale Success: {}", response.getBody());
                
                return ErpPostingResult.builder()
                        .success(true)
                        .documentNo(extractSlipNos(root))
                        .rawResponse(response.getBody())
                        .build();
            } else {
                String errorMsg = extractErrorMessage(root);
                log.warn("[Ecount] SaveSale Failed: {}", errorMsg);
                
                // 세션 만료 시 재시도
                if (isSessionExpired(root)) {
                    sessionService.invalidateSession(tenantId);
                    return saveSaleForms(tenantId, request);
                }
                
                return ErpPostingResult.builder()
                        .success(false)
                        .errorCode(extractErrorCode(root))
                        .errorMessage(errorMsg)
                        .rawResponse(response.getBody())
                        .build();
            }

        } catch (Exception e) {
            log.error("[Ecount] Failed to save sale forms", e);
            return ErpPostingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 전표번호 추출 (여러 전표가 생성된 경우 쉼표로 구분)
     */
    private String extractSlipNos(JsonNode root) {
        JsonNode slipNos = root.path("Data").path("SlipNos");
        if (slipNos.isArray() && slipNos.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slipNos.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(slipNos.get(i).asText());
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * 창고재고현황 조회
     * - 품목별 재고 수량을 조회
     * - 주의: 재고현황 API는 sboapi 도메인을 사용 (다른 API는 oapi 사용)
     */
    public Map<String, InventoryBalance> getInventoryBalances(UUID tenantId, String baseDate) {
        EcountCredentials creds = getCredentials(tenantId);
        String sessionId = sessionService.getSessionId(tenantId, creds);
        
        // Zone은 getCredentials()에서 이미 확인 및 DB 저장 완료
        String zone = creds.getZone().trim();
        
        // 재고현황 API는 sboapi 도메인 사용 (zone이 AC인 경우 sboapiAC.ecount.com)
        String url = String.format("https://oapi%s.ecount.com/OAPI/V2/InventoryBalance/GetListInventoryBalanceStatusByLocation?SESSION_ID=%s",
                zone, sessionId);
        
        log.info("[Ecount] Inventory balance API URL: zone=[{}], final_url={}", 
                zone, url.replace(sessionId, "***SESSION***"));

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("BASE_DATE", baseDate); // YYYYMMDD 형식
            body.put("BAL_FLAG", "Y"); // 수량만 조회
            body.put("DEL_GUBUN", "Y"); // 삭제 품목 포함
            body.put("DEL_LOCATION_YN", "Y"); // 위치별 조회

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            log.info("[Ecount] GetListInventoryBalanceStatusByLocation Request: URL={}, BASE_DATE={}", 
                    url.replace(sessionId, "***SESSION***"), baseDate);

            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            String responseBody = response.getBody();

            if (responseBody == null) {
                throw new EcountApiException("Empty response from Ecount API");
            }

            log.debug("[Ecount] GetListInventoryBalanceStatusByLocation Response: Status={}, Body={}",
                    response.getStatusCode(),
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

            JsonNode root = objectMapper.readTree(responseBody);

            if (isSuccess(root)) {
                Map<String, InventoryBalance> balances = parseInventoryBalances(root.path("Data").path("Result"));
                log.info("[Ecount] GetListInventoryBalanceStatusByLocation Success: {} items", balances.size());
                return balances;
            } else {
                if (isSessionExpired(root)) {
                    log.warn("[Ecount] Session expired, retrying...");
                    sessionService.invalidateSession(tenantId);
                    return getInventoryBalances(tenantId, baseDate);
                }
                String errorMsg = extractErrorMessage(root);
                log.error("[Ecount] GetListInventoryBalanceStatusByLocation Failed: {}", errorMsg);
                throw new EcountApiException(errorMsg);
            }

        } catch (EcountApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Ecount] Failed to get inventory balances", e);
            throw new EcountApiException("Failed to get inventory balances", e);
        }
    }

    /**
     * 재고 현황 DTO
     */
    public static class InventoryBalance {
        private String itemCode;      // PROD_CD
        private String warehouseCode;  // WH_CD
        private String warehouseName;  // WH_DES
        private Double balanceQty;     // BAL_QTY (재고수량)
        
        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        
        public String getWarehouseCode() { return warehouseCode; }
        public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
        
        public String getWarehouseName() { return warehouseName; }
        public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
        
        public Double getBalanceQty() { return balanceQty; }
        public void setBalanceQty(Double balanceQty) { this.balanceQty = balanceQty; }
    }

    // === Private Methods ===

    private EcountCredentials getCredentials(UUID tenantId) {
        String credJson = credentialService.getDecryptedCredential(tenantId, null, "ERP", "ECOUNT_CONFIG");
        EcountCredentials creds = EcountCredentials.parse(credJson);
        
        // Zone이 없으면 조회하고 DB에 저장
        if (creds.getZone() == null || creds.getZone().trim().isEmpty()) {
            log.info("[Ecount] Zone not found in DB, fetching zone for comCode={}", creds.getComCode());
            String zone = sessionService.getZone(creds.getComCode());
            creds.setZone(zone);
            
            // DB에 zone 정보 업데이트
            try {
                String updatedJson = creds.toJson();
                credentialService.saveCredential(tenantId, null, "ERP", "ECOUNT_CONFIG", updatedJson);
                log.info("[Ecount] Zone saved to DB: tenantId={}, zone={}", tenantId, zone);
            } catch (Exception e) {
                log.warn("[Ecount] Failed to save zone to DB: {}", e.getMessage());
                // zone은 메모리에 있으므로 계속 진행
            }
        }
        
        return creds;
    }


    private Map<String, Object> buildSalesDocumentPayload(ErpSalesDocumentRequest request) {
        List<Map<String, Object>> saleList = new ArrayList<>();
        
        for (int i = 0; i < request.getLines().size(); i++) {
            ErpSalesLine line = request.getLines().get(i);
            
            // BulkDatas 객체 생성
            Map<String, Object> bulkData = new HashMap<>();
            bulkData.put("UPLOAD_SER_NO", i + 1);  // 같은 전표로 묶으려면 동일한 값 사용
            bulkData.put("IO_DATE", request.getDocumentDate().replace("-", ""));
            bulkData.put("CUST", request.getCustomerCode());  // CUST_CD가 아니라 CUST
            bulkData.put("WH_CD", request.getWarehouseCode() != null ? request.getWarehouseCode() : "100");
            bulkData.put("PROD_CD", line.getItemCode());
            bulkData.put("PROD_DES", line.getItemName());
            bulkData.put("QTY", line.getQuantity());
            bulkData.put("USER_PRICE_VAT", line.getUnitPrice());  // VAT 포함 단가
            bulkData.put("SUPPLY_AMT", line.getAmount());
            bulkData.put("VAT_AMT", line.getVatAmount() != null ? line.getVatAmount() : 0);
            bulkData.put("REMARKS", line.getRemarks() != null ? line.getRemarks() : "");
            
            // SaleList 항목 생성 (BulkDatas로 감싸기)
            Map<String, Object> saleItem = new HashMap<>();
            saleItem.put("BulkDatas", bulkData);
            saleList.add(saleItem);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("SaleList", saleList);
        
        return body;
    }

    private boolean isSuccess(JsonNode root) {
        String status = root.path("Status").asText();
        return "200".equals(status);
    }

    private boolean isSessionExpired(JsonNode root) {
        String errorCode = root.path("Error").path("Code").asText();
        return "SESSION_EXPIRED".equals(errorCode) || "INVALID_SESSION".equals(errorCode);
    }

    private String extractDocumentNo(JsonNode root) {
        JsonNode data = root.path("Data").path("Datas");
        if (data.isArray() && data.size() > 0) {
            return data.get(0).path("IO_NO").asText();
        }
        return null;
    }

    private String extractErrorCode(JsonNode root) {
        return root.path("Error").path("Code").asText("UNKNOWN");
    }

    private String extractErrorMessage(JsonNode root) {
        return root.path("Error").path("Message").asText("Unknown error");
    }

    private List<ErpItemDto> parseItems(JsonNode datasNode) {
        List<ErpItemDto> items = new ArrayList<>();
        
        if (datasNode.isMissingNode() || datasNode.isNull()) {
            log.warn("[Ecount] Items data node is missing or null");
            return items;
        }
        
        if (datasNode.isArray()) {
            log.debug("[Ecount] Parsing {} item records", datasNode.size());
            for (JsonNode node : datasNode) {
                try {
                    // 재고 수량 파싱 (여러 필드명 시도)
                    Integer stockQty = null;
                    if (node.has("STOCK_QTY")) {
                        stockQty = node.path("STOCK_QTY").asInt(0);
                    } else if (node.has("QTY")) {
                        stockQty = node.path("QTY").asInt(0);
                    } else if (node.has("STOCK_AMT")) {
                        stockQty = node.path("STOCK_AMT").asInt(0);
                    }
                    
                    // 가용 수량 파싱
                    Integer availableQty = null;
                    if (node.has("AVAIL_QTY")) {
                        availableQty = node.path("AVAIL_QTY").asInt(0);
                    } else if (node.has("AVAILABLE_QTY")) {
                        availableQty = node.path("AVAILABLE_QTY").asInt(0);
                    }
                    
                    items.add(ErpItemDto.builder()
                            .itemCode(node.path("PROD_CD").asText())
                            .itemName(node.path("PROD_DES").asText())
                            .itemSpec(node.path("SIZE_DES").asText(null))
                            .unit(node.path("UNIT").asText(null))
                            .unitPrice(node.path("OUT_PRICE").asLong(0))
                            .itemType(node.path("PROD_TYPE").asText(null))
                            .categoryCode(node.path("CLASS_CD").asText(null))
                            .categoryName(node.path("CLASS_DES").asText(null))
                            .stockQty(stockQty)
                            .availableQty(availableQty)
                            .isActive(!"N".equals(node.path("USE_YN").asText("Y")))
                            .build());
                } catch (Exception e) {
                    log.warn("[Ecount] Failed to parse item: {}", node.toString(), e);
                }
            }
        } else {
            log.warn("[Ecount] Items data is not an array: {}", datasNode.getNodeType());
        }
        
        return items;
    }

    private List<ErpCustomerDto> parseCustomers(JsonNode datasNode) {
        List<ErpCustomerDto> customers = new ArrayList<>();
        
        if (datasNode.isArray()) {
            for (JsonNode node : datasNode) {
                customers.add(ErpCustomerDto.builder()
                        .customerCode(node.path("CUST_CD").asText())
                        .customerName(node.path("CUST_DES").asText())
                        .bizNo(node.path("SAUP_NO").asText(null))
                        .ceoName(node.path("DAEPYO").asText(null))
                        .customerType(node.path("CUST_TYPE").asText(null))
                        .isActive(!"N".equals(node.path("USE_YN").asText("Y")))
                        .build());
            }
        }
        
        return customers;
    }

    /**
     * 재고 현황 파싱
     * - 품목 코드를 키로 하는 맵 반환
     * - 여러 창고에 동일 품목이 있는 경우, 재고가 가장 많은 창고를 주 창고로 선택
     */
    private Map<String, InventoryBalance> parseInventoryBalances(JsonNode datasNode) {
        Map<String, InventoryBalance> balanceMap = new HashMap<>();
        
        if (datasNode.isMissingNode() || datasNode.isNull()) {
            log.warn("[Ecount] Inventory balance data node is missing or null");
            return balanceMap;
        }
        
        if (datasNode.isArray()) {
            log.debug("[Ecount] Parsing {} inventory balance records", datasNode.size());
            
            // 품목별 창고 목록 (재고량 포함)
            Map<String, List<InventoryBalance>> itemWarehousesMap = new HashMap<>();
            
            for (JsonNode node : datasNode) {
                try {
                    String prodCd = node.path("PROD_CD").asText();
                    String whCd = node.path("WH_CD").asText();
                    String whDes = node.path("WH_DES").asText();
                    double balQty = node.path("BAL_QTY").asDouble(0.0);
                    
                    if (prodCd != null && !prodCd.isEmpty()) {
                        InventoryBalance balance = new InventoryBalance();
                        balance.setItemCode(prodCd);
                        balance.setWarehouseCode(whCd);
                        balance.setWarehouseName(whDes);
                        balance.setBalanceQty(balQty);
                        
                        itemWarehousesMap.computeIfAbsent(prodCd, k -> new ArrayList<>())
                                .add(balance);
                    }
                } catch (Exception e) {
                    log.warn("[Ecount] Failed to parse inventory balance: {}", node.toString(), e);
                }
            }
            
            // 품목별로 재고가 가장 많은 창고를 주 창고로 선택
            for (Map.Entry<String, List<InventoryBalance>> entry : itemWarehousesMap.entrySet()) {
                String prodCd = entry.getKey();
                List<InventoryBalance> warehouses = entry.getValue();
                
                // 재고량 기준 내림차순 정렬 (재고가 가장 많은 창고가 첫 번째)
                warehouses.sort((a, b) -> Double.compare(b.getBalanceQty(), a.getBalanceQty()));
                
                // 총 재고량 계산
                double totalQty = warehouses.stream()
                        .mapToDouble(InventoryBalance::getBalanceQty)
                        .sum();
                
                // 주 창고 (재고가 가장 많은 창고)
                InventoryBalance mainWarehouse = warehouses.get(0);
                mainWarehouse.setBalanceQty(totalQty); // 총 재고량으로 업데이트
                
                balanceMap.put(prodCd, mainWarehouse);
                
                if (warehouses.size() > 1) {
                    log.debug("[Ecount] Item {} found in {} warehouses, selected {} (qty: {}) as main warehouse", 
                            prodCd, warehouses.size(), mainWarehouse.getWarehouseCode(), totalQty);
                }
            }
            
            log.info("[Ecount] Parsed {} unique items with inventory balances", balanceMap.size());
        } else {
            log.warn("[Ecount] Inventory balance data is not an array: {}", datasNode.getNodeType());
        }
        
        return balanceMap;
    }
}
