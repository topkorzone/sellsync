package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.repository.ErpConfigRepository;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 정산 수집된 주문에 대한 전표 생성 서비스 (개선 버전)
 * 
 * 역할:
 * - settlement_status = COLLECTED인 주문에 대해 정산 전표 생성
 * - 한 주문당 하나의 전표로 생성 (SaleList 내 복수 BulkDatas):
 *   1. 상품 매출 (PRODUCT_SALES)
 *   2. 배송비 (SHIPPING_FEE) - 있는 경우만
 *   3. 상품판매 수수료 (COMMISSION_EXPENSE)
 *   4. 배송비 수수료 (SHIPPING_COMMISSION) - 있는 경우만
 * - 수수료 품목 코드는 ERP 설정에서 조회하여 자동 입력
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSettlementPostingService {

    private final OrderRepository orderRepository;
    private final PostingService postingService;
    private final ErpConfigRepository erpConfigRepository;
    private final ErpItemRepository erpItemRepository;
    private final StoreRepository storeRepository;
    private final ProductMappingService productMappingService;

    private static final DateTimeFormatter IO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 정산 수집된 주문에 대한 전표 생성 (한 셋트로 통합)
     * 
     * @param orderId 주문 ID
     * @param erpCode ERP 코드 (ECOUNT 등)
     * @return 생성된 전표 (1개만 생성됨)
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws IllegalStateException settlement_status가 COLLECTED가 아닌 경우
     */
    @Transactional
    public PostingResponse createPostingsForSettledOrder(UUID orderId, String erpCode) {
        log.info("[정산 전표 생성 시작] orderId={}, erpCode={}", orderId, erpCode);

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. settlement_status = COLLECTED 확인
        if (order.getSettlementStatus() != SettlementCollectionStatus.COLLECTED) {
            String errorMsg = String.format(
                "주문의 정산 상태가 COLLECTED가 아닙니다. orderId=%s, settlementStatus=%s",
                orderId, order.getSettlementStatus()
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 3. ERP 설정 조회 (수수료 품목 코드 확인용)
        ErpConfig erpConfig = erpConfigRepository.findByTenantIdAndErpCode(order.getTenantId(), erpCode)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("ERP 설정이 없습니다. tenantId=%s, erpCode=%s", order.getTenantId(), erpCode)
                ));

        // 4. Store 조회 (거래처 코드 확인용)
        Store store = storeRepository.findById(order.getStoreId())
                .orElseThrow(() -> new IllegalStateException(
                    String.format("스토어를 찾을 수 없습니다. storeId=%s", order.getStoreId())
                ));

        // 5. 통합 전표 Payload 생성 (상품판매 + 배송비 + 수수료)
        String payload = buildIntegratedPostingPayload(order, erpConfig, store);

        // 6. 전표 생성 (PostingType은 PRODUCT_SALES로 대표)
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode(erpCode)
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.PRODUCT_SALES) // 통합 전표의 대표 타입
                .requestPayload(payload)
                .build();

        PostingResponse posting = postingService.createOrGet(request);
        log.info("[통합 전표 생성] postingId={}", posting.getPostingId());

        // 7. 정산 전표 생성 완료 마킹
        order.markSettlementPosted();
        orderRepository.save(order);
        log.info("[정산 전표 생성 완료 마킹] orderId={}, settlementStatus={}", 
            orderId, order.getSettlementStatus());

        log.info("[정산 전표 생성 완료] orderId={}, postingId={}", orderId, posting.getPostingId());

        return posting;
    }

    // ========== Private Helper Methods ==========

    /**
     * 통합 전표 Payload 생성
     * 
     * SaleList 구조:
     * - BulkDatas[0]: 상품판매 (PRODUCT_SALES)
     * - BulkDatas[1]: 배송비 (SHIPPING_FEE) - 있으면
     * - BulkDatas[2]: 상품판매 수수료 (COMMISSION_EXPENSE)
     * - BulkDatas[3]: 배송비 수수료 (SHIPPING_COMMISSION) - 있으면
     */
    private String buildIntegratedPostingPayload(Order order, ErpConfig erpConfig, Store store) {
        log.debug("[통합 Payload 생성 시작] orderId={}", order.getOrderId());

        List<Map<String, Object>> saleList = new ArrayList<>();
        String ioDate = order.getPaidAt().format(IO_DATE_FORMATTER);
        String uploadSerNo = UUID.randomUUID().toString();

        int index = 0;

        // 1. 상품판매 전표 (필수)
        if (order.getTotalProductAmount() != null && order.getTotalProductAmount() > 0) {
            Map<String, Object> productSales = createProductSalesBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store
            );
            saleList.add(Collections.singletonMap("BulkDatas", productSales));
            log.debug("[상품판매 전표 추가] index={}, amount={}", index, order.getTotalProductAmount());
        }

        // 2. 배송비 전표 (있는 경우만)
        log.debug("[배송비 확인] totalShippingAmount={}, shippingFee={}", 
            order.getTotalShippingAmount(), order.getShippingFee());
        
        // totalShippingAmount 또는 shippingFee가 있으면 배송비 전표 생성
        Long shippingAmount = order.getTotalShippingAmount();
        if (shippingAmount == null || shippingAmount == 0) {
            shippingAmount = order.getShippingFee();
        }
        
        if (shippingAmount != null && shippingAmount > 0) {
            Map<String, Object> shippingFee = createShippingFeeBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store, shippingAmount
            );
            saleList.add(Collections.singletonMap("BulkDatas", shippingFee));
            log.debug("[배송비 전표 추가] index={}, amount={}", index, shippingAmount);
        } else {
            log.info("[배송비 없음] orderId={}, totalShippingAmount={}, shippingFee={}", 
                order.getOrderId(), order.getTotalShippingAmount(), order.getShippingFee());
        }

        // 3. 상품판매 수수료 전표 (필수)
        if (order.getCommissionAmount() != null && order.getCommissionAmount() > 0) {
            Map<String, Object> commission = createCommissionBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store
            );
            saleList.add(Collections.singletonMap("BulkDatas", commission));
            log.debug("[상품판매 수수료 전표 추가] index={}, amount={}", index, order.getCommissionAmount());
        }

        // 4. 배송비 수수료 전표 (배송비가 있고 배송비 수수료가 있으면)
        Long shippingCommission = order.getShippingCommissionAmount();
        if (shippingAmount != null && shippingAmount > 0 
                && shippingCommission != null && shippingCommission > 0
                && store.getShippingCommissionItemCode() != null 
                && !store.getShippingCommissionItemCode().isEmpty()) {
            Map<String, Object> shippingCommissionData = createShippingCommissionBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store, shippingCommission
            );
            saleList.add(Collections.singletonMap("BulkDatas", shippingCommissionData));
            log.debug("[배송비 수수료 전표 추가] index={}, amount={}", index, shippingCommission);
        } else {
            log.info("[배송비 수수료 생략] shippingAmount={}, shippingCommission={}, commissionItemCode={}", 
                shippingAmount, shippingCommission, store.getShippingCommissionItemCode());
        }

        // JSON 직렬화
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("SaleList", saleList);
            
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[통합 Payload 생성 실패] orderId={}", order.getOrderId(), e);
            throw new RuntimeException("통합 전표 Payload 생성 실패", e);
        }
    }

    /**
     * 상품판매 BulkData 생성
     */
    private Map<String, Object> createProductSalesBulkData(
            Order order, String ioDate, String uploadSerNo, int index, ErpConfig erpConfig, Store store) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("UPLOAD_SER_NO", uploadSerNo);
        data.put("IO_DATE", ioDate);
        
        // 거래처 코드: Store의 erpCustomerCode 사용
        String customerCode = store.getErpCustomerCode() != null ? 
            store.getErpCustomerCode() : erpConfig.getDefaultCustomerCode();
        data.put("CUST", customerCode);
        
        // 상품 정보 및 창고 코드 (첫 번째 아이템 기준)
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            OrderItem firstItem = order.getItems().get(0);
            
            log.info("[상품 매핑 조회 시작] orderId={}, productId={}, sku={}", 
                order.getOrderId(), firstItem.getMarketplaceProductId(), firstItem.getMarketplaceSku());
            
            // ProductMapping에서 ERP 품목 코드 조회
            Optional<ProductMappingResponse> mappingOpt = productMappingService.findActiveMapping(
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace(),
                firstItem.getMarketplaceProductId(),
                firstItem.getMarketplaceSku()
            );
            
            if (mappingOpt.isPresent()) {
                ProductMappingResponse mapping = mappingOpt.get();
                String erpItemCode = mapping.getErpItemCode();
                
                log.info("[상품 매핑 조회 성공] erpItemCode={}", erpItemCode);
                
                // ERP 품목에서 창고 코드 조회
                Optional<ErpItem> erpItemOpt = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
                    order.getTenantId(), erpConfig.getErpCode(), erpItemCode
                );
                
                if (erpItemOpt.isPresent()) {
                    ErpItem erpItem = erpItemOpt.get();
                    data.put("PROD_CD", erpItem.getItemCode());
                    data.put("PROD_DES", erpItem.getItemName());
                    String whCode = erpItem.getWarehouseCode() != null ? 
                        erpItem.getWarehouseCode() : 
                        (erpConfig.getDefaultWarehouseCode() != null ? erpConfig.getDefaultWarehouseCode() : "100");
                    data.put("WH_CD", whCode);
                    log.info("[ERP 품목 조회 성공] itemCode={}, warehouseCode={}", 
                        erpItem.getItemCode(), whCode);
                } else {
                    // ERP 품목이 없으면 매핑 정보 사용
                    data.put("PROD_CD", erpItemCode);
                    data.put("PROD_DES", mapping.getErpItemName());
                    String whCode = mapping.getWarehouseCode() != null ?
                        mapping.getWarehouseCode() :
                        (erpConfig.getDefaultWarehouseCode() != null ? erpConfig.getDefaultWarehouseCode() : "100");
                    data.put("WH_CD", whCode);
                    log.warn("[ERP 품목 없음] tenantId={}, erpCode={}, itemCode={} - 매핑 정보 사용", 
                        order.getTenantId(), erpConfig.getErpCode(), erpItemCode);
                }
            } else {
                // 매핑 정보가 없으면 마켓 SKU 사용 (fallback)
                data.put("PROD_CD", firstItem.getMarketplaceSku());
                data.put("PROD_DES", firstItem.getProductName());
                data.put("WH_CD", erpConfig.getDefaultWarehouseCode() != null ? 
                    erpConfig.getDefaultWarehouseCode() : "100");
                log.error("[상품 매핑 없음] orderId={}, productId={}, sku={} - SKU를 PROD_CD로 사용", 
                    order.getOrderId(), firstItem.getMarketplaceProductId(), firstItem.getMarketplaceSku());
            }
            
            data.put("QTY", firstItem.getQuantity());
        } else {
            data.put("PROD_CD", "UNKNOWN");
            data.put("PROD_DES", "상품판매");
            data.put("QTY", 1);
            data.put("WH_CD", erpConfig.getDefaultWarehouseCode() != null ? 
                erpConfig.getDefaultWarehouseCode() : "100");
            log.error("[주문 아이템 없음] orderId={}", order.getOrderId());
        }
        
        // 금액 계산 (VAT 포함/미포함)
        long totalAmount = order.getTotalProductAmount();
        BigDecimal vatRate = new BigDecimal("0.1"); // VAT 10%
        long supplyAmt = calculateSupplyAmount(totalAmount, vatRate);
        long vatAmt = totalAmount - supplyAmt;
        
        data.put("USER_PRICE_VAT", totalAmount);
        data.put("SUPPLY_AMT", supplyAmt);
        data.put("VAT_AMT", vatAmt);
        data.put("P_AMT1", totalAmount);
        
        data.put("REMARKS", String.format("주문번호: %s %s", 
            order.getMarketplaceOrderId(), order.getBuyerName()));
        data.put("P_REMARKS1", String.format("주문번호: %s %s", 
            order.getMarketplaceOrderId(), order.getBuyerName()));
        
        return data;
    }

    /**
     * 배송비 BulkData 생성
     */
    private Map<String, Object> createShippingFeeBulkData(
            Order order, String ioDate, String uploadSerNo, int index, ErpConfig erpConfig, Store store, Long shippingAmount) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("UPLOAD_SER_NO", uploadSerNo);
        data.put("IO_DATE", ioDate);
        
        // 거래처 코드: Store의 erpCustomerCode 사용
        String customerCode = store.getErpCustomerCode() != null ? 
            store.getErpCustomerCode() : erpConfig.getDefaultCustomerCode();
        data.put("CUST", customerCode);
        
        // 배송비 품목 (ERP 품목에서 조회)
        String shippingItemCode = erpConfig.getShippingItemCode() != null ? 
            erpConfig.getShippingItemCode() : "SHIPPING";
        
        log.info("[배송비 품목 조회] tenantId={}, erpCode={}, itemCode={}", 
            order.getTenantId(), erpConfig.getErpCode(), shippingItemCode);
        
        Optional<ErpItem> shippingItem = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
            order.getTenantId(), erpConfig.getErpCode(), shippingItemCode
        );
        
        if (shippingItem.isPresent()) {
            ErpItem item = shippingItem.get();
            data.put("PROD_CD", item.getItemCode());
            data.put("PROD_DES", item.getItemName());
            String whCode = item.getWarehouseCode() != null ? 
                item.getWarehouseCode() : 
                (erpConfig.getDefaultWarehouseCode() != null ? erpConfig.getDefaultWarehouseCode() : "100");
            data.put("WH_CD", whCode);
            log.info("[배송비 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), whCode);
        } else {
            data.put("PROD_CD", shippingItemCode);
            data.put("PROD_DES", "배송비");
            data.put("WH_CD", erpConfig.getDefaultWarehouseCode() != null ? 
                erpConfig.getDefaultWarehouseCode() : "100");
            log.warn("[배송비 품목 없음] tenantId={}, erpCode={}, itemCode={} - 기본값 사용", 
                order.getTenantId(), erpConfig.getErpCode(), shippingItemCode);
        }
        
        data.put("QTY", 1);
        data.put("UNIT", "");
        
        // 금액 계산
        long totalAmount = shippingAmount;
        BigDecimal vatRate = new BigDecimal("0.1");
        long supplyAmt = calculateSupplyAmount(totalAmount, vatRate);
        long vatAmt = totalAmount - supplyAmt;
        
        data.put("USER_PRICE_VAT", totalAmount);
        data.put("SUPPLY_AMT", supplyAmt);
        data.put("VAT_AMT", vatAmt);
        data.put("P_AMT1", totalAmount);
        
        data.put("REMARKS", order.getBuyerName());
        data.put("P_REMARKS1", order.getBuyerName());
        
        return data;
    }

    /**
     * 상품판매 수수료 BulkData 생성
     */
    private Map<String, Object> createCommissionBulkData(
            Order order, String ioDate, String uploadSerNo, int index, ErpConfig erpConfig, Store store) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("UPLOAD_SER_NO", uploadSerNo);
        data.put("IO_DATE", ioDate);
        
        // 거래처 코드: Store의 erpCustomerCode 사용
        String customerCode = store.getErpCustomerCode() != null ? 
            store.getErpCustomerCode() : erpConfig.getDefaultCustomerCode();
        data.put("CUST", customerCode);
        
        // 수수료 품목 (Store에서 조회)
        String commissionItemCode = store.getCommissionItemCode();
        if (commissionItemCode == null || commissionItemCode.isEmpty()) {
            // Store에 없으면 ErpConfig에서 fallback
            commissionItemCode = erpConfig.getCommissionItemCode();
            if (commissionItemCode == null || commissionItemCode.isEmpty()) {
                commissionItemCode = "COMMISSION"; // 최종 기본값
                log.warn("[수수료 품목 코드 미설정] storeId={}, tenantId={} - 기본값 'COMMISSION' 사용", 
                    order.getStoreId(), order.getTenantId());
            }
        }
        
        log.info("[수수료 품목 조회] tenantId={}, erpCode={}, itemCode={}", 
            order.getTenantId(), erpConfig.getErpCode(), commissionItemCode);
        
        Optional<ErpItem> commissionItem = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
            order.getTenantId(), erpConfig.getErpCode(), commissionItemCode
        );
        
        if (commissionItem.isPresent()) {
            ErpItem item = commissionItem.get();
            data.put("PROD_CD", item.getItemCode());
            data.put("PROD_DES", item.getItemName());
            String whCode = item.getWarehouseCode() != null ? 
                item.getWarehouseCode() : 
                (erpConfig.getDefaultWarehouseCode() != null ? erpConfig.getDefaultWarehouseCode() : "100");
            data.put("WH_CD", whCode);
            log.info("[수수료 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), whCode);
        } else {
            data.put("PROD_CD", commissionItemCode);
            data.put("PROD_DES", erpConfig.getCommissionItemName() != null ? 
                erpConfig.getCommissionItemName() : "스마트스토어 수수료");
            data.put("WH_CD", erpConfig.getDefaultWarehouseCode() != null ? 
                erpConfig.getDefaultWarehouseCode() : "100");
            log.warn("[수수료 품목 없음] tenantId={}, erpCode={}, itemCode={} - 기본값 사용", 
                order.getTenantId(), erpConfig.getErpCode(), commissionItemCode);
        }
        
        data.put("QTY", 1);
        data.put("UNIT", "");
        
        // 금액 계산 (수수료는 마이너스)
        long commissionAmount = -order.getCommissionAmount(); // 비용이므로 음수
        BigDecimal vatRate = new BigDecimal("0.1");
        long supplyAmt = calculateSupplyAmount(Math.abs(commissionAmount), vatRate);
        long vatAmt = Math.abs(commissionAmount) - supplyAmt;
        
        // 마이너스 유지
        if (commissionAmount < 0) {
            supplyAmt = -supplyAmt;
            vatAmt = -vatAmt;
        }
        
        data.put("USER_PRICE_VAT", commissionAmount);
        data.put("SUPPLY_AMT", supplyAmt);
        data.put("VAT_AMT", vatAmt);
        data.put("P_AMT1", commissionAmount);
        
        data.put("REMARKS", order.getBuyerName());
        data.put("P_REMARKS1", order.getBuyerName());
        
        return data;
    }

    /**
     * 배송비 수수료 BulkData 생성
     */
    private Map<String, Object> createShippingCommissionBulkData(
            Order order, String ioDate, String uploadSerNo, int index, 
            ErpConfig erpConfig, Store store, long shippingCommission) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("UPLOAD_SER_NO", uploadSerNo);
        data.put("IO_DATE", ioDate);
        
        // 거래처 코드: Store의 erpCustomerCode 사용
        String customerCode = store.getErpCustomerCode() != null ? 
            store.getErpCustomerCode() : erpConfig.getDefaultCustomerCode();
        data.put("CUST", customerCode);
        
        // 배송비 수수료 품목 (Store에서 조회)
        String shippingCommissionItemCode = store.getShippingCommissionItemCode();
        if (shippingCommissionItemCode == null || shippingCommissionItemCode.isEmpty()) {
            // Store에 없으면 ErpConfig에서 fallback
            shippingCommissionItemCode = erpConfig.getShippingCommissionItemCode();
        }
        
        log.info("[배송비 수수료 품목 조회] tenantId={}, erpCode={}, itemCode={}", 
            order.getTenantId(), erpConfig.getErpCode(), shippingCommissionItemCode);
        
        Optional<ErpItem> shippingCommissionItem = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
            order.getTenantId(), erpConfig.getErpCode(), shippingCommissionItemCode
        );
        
        if (shippingCommissionItem.isPresent()) {
            ErpItem item = shippingCommissionItem.get();
            data.put("PROD_CD", item.getItemCode());
            data.put("PROD_DES", item.getItemName());
            String whCode = item.getWarehouseCode() != null ? 
                item.getWarehouseCode() : 
                (erpConfig.getDefaultWarehouseCode() != null ? erpConfig.getDefaultWarehouseCode() : "100");
            data.put("WH_CD", whCode);
            log.info("[배송비 수수료 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), whCode);
        } else {
            data.put("PROD_CD", shippingCommissionItemCode);
            data.put("PROD_DES", erpConfig.getShippingCommissionItemName() != null ? 
                erpConfig.getShippingCommissionItemName() : "배송비 수수료");
            data.put("WH_CD", erpConfig.getDefaultWarehouseCode() != null ? 
                erpConfig.getDefaultWarehouseCode() : "100");
            log.warn("[배송비 수수료 품목 없음] tenantId={}, erpCode={}, itemCode={} - 기본값 사용", 
                order.getTenantId(), erpConfig.getErpCode(), shippingCommissionItemCode);
        }
        
        data.put("QTY", 1);
        data.put("UNIT", "");
        
        // 금액 계산 (수수료는 마이너스)
        long commissionAmount = -shippingCommission;
        BigDecimal vatRate = new BigDecimal("0.1");
        long supplyAmt = -calculateSupplyAmount(shippingCommission, vatRate);
        long vatAmt = commissionAmount - supplyAmt;
        
        data.put("USER_PRICE_VAT", commissionAmount);
        data.put("SUPPLY_AMT", supplyAmt);
        data.put("VAT_AMT", vatAmt);
        data.put("P_AMT1", commissionAmount);
        
        data.put("REMARKS", order.getBuyerName());
        data.put("P_REMARKS1", order.getBuyerName());
        
        return data;
    }

    /**
     * 공급가액 계산 (VAT 제외)
     * 
     * @param totalAmount VAT 포함 금액
     * @param vatRate VAT 비율 (예: 0.1)
     * @return 공급가액
     */
    private long calculateSupplyAmount(long totalAmount, BigDecimal vatRate) {
        BigDecimal total = BigDecimal.valueOf(totalAmount);
        BigDecimal divisor = BigDecimal.ONE.add(vatRate);
        return total.divide(divisor, 0, RoundingMode.HALF_UP).longValue();
    }
}
