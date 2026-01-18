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
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import com.sellsync.api.domain.settlement.repository.SettlementOrderRepository;
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
    private final SettlementOrderRepository settlementOrderRepository;

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
        
        // UPLOAD_SER_NO를 정수형으로 생성 (당일 기준 시퀀스)
        Integer uploadSerNo = generateDailySequence(order);
        
        // 상품 전표의 창고코드를 모든 전표에서 공통으로 사용
        String commonWhCd = getProductWarehouseCode(order, erpConfig);
        log.info("[창고코드 통일] orderId={}, commonWhCd={}", order.getOrderId(), commonWhCd);

        int index = 0;

        // 1. 상품판매 전표 (필수)
        if (order.getTotalProductAmount() != null && order.getTotalProductAmount() > 0) {
            Map<String, Object> productSales = createProductSalesBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store, commonWhCd
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
                order, ioDate, uploadSerNo, ++index, erpConfig, store, shippingAmount, commonWhCd
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
                order, ioDate, uploadSerNo, ++index, erpConfig, store, commonWhCd
            );
            saleList.add(Collections.singletonMap("BulkDatas", commission));
            log.debug("[상품판매 수수료 전표 추가] index={}, amount={}", index, order.getCommissionAmount());
        }

        // 4. 배송비 수수료 전표 (배송비가 있고 배송비 수수료가 있으면)
        // 정산 테이블에서 SHIPPING_FEE 타입의 수수료 조회
        Long shippingCommission = getShippingFeeCommissionFromSettlement(order);
        
        log.debug("[배송비 수수료 확인] shippingCommission={}, itemCode={}", 
            shippingCommission, store.getShippingCommissionItemCode());
        
        if (shippingAmount != null && shippingAmount > 0 
                && shippingCommission != null && shippingCommission > 0
                && store.getShippingCommissionItemCode() != null 
                && !store.getShippingCommissionItemCode().isEmpty()) {
            Map<String, Object> shippingCommissionData = createShippingCommissionBulkData(
                order, ioDate, uploadSerNo, ++index, erpConfig, store, shippingCommission, commonWhCd
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
            Order order, String ioDate, Integer uploadSerNo, int index, ErpConfig erpConfig, Store store, String commonWhCd) {
        
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
                    // 창고코드는 commonWhCd 사용 (통일)
                    data.put("WH_CD", commonWhCd);
                    log.info("[ERP 품목 조회 성공] itemCode={}, warehouseCode={}", 
                        erpItem.getItemCode(), commonWhCd);
                } else {
                    // ERP 품목이 없으면 매핑 정보 사용
                    data.put("PROD_CD", erpItemCode);
                    data.put("PROD_DES", mapping.getErpItemName());
                    // 창고코드는 commonWhCd 사용 (통일)
                    data.put("WH_CD", commonWhCd);
                    log.warn("[ERP 품목 없음] tenantId={}, erpCode={}, itemCode={} - 매핑 정보 사용", 
                        order.getTenantId(), erpConfig.getErpCode(), erpItemCode);
                }
            } else {
                // 매핑 정보가 없으면 마켓 SKU 사용 (fallback)
                data.put("PROD_CD", firstItem.getMarketplaceSku());
                data.put("PROD_DES", firstItem.getProductName());
                // 창고코드는 commonWhCd 사용 (통일)
                data.put("WH_CD", commonWhCd);
                log.error("[상품 매핑 없음] orderId={}, productId={}, sku={} - SKU를 PROD_CD로 사용", 
                    order.getOrderId(), firstItem.getMarketplaceProductId(), firstItem.getMarketplaceSku());
            }
            
            data.put("QTY", firstItem.getQuantity());
        } else {
            data.put("PROD_CD", "UNKNOWN");
            data.put("PROD_DES", "상품판매");
            data.put("QTY", 1);
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
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
            Order order, String ioDate, Integer uploadSerNo, int index, ErpConfig erpConfig, Store store, Long shippingAmount, String commonWhCd) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("UPLOAD_SER_NO", uploadSerNo);
        data.put("IO_DATE", ioDate);
        
        // 거래처 코드: Store의 erpCustomerCode 사용
        String customerCode = store.getErpCustomerCode() != null ? 
            store.getErpCustomerCode() : erpConfig.getDefaultCustomerCode();
        data.put("CUST", customerCode);
        
        // 배송비 품목 (Store 설정에서 조회, 없으면 ErpConfig에서 조회)
        String shippingItemCode = store.getShippingItemCode() != null && !store.getShippingItemCode().isEmpty() ?
            store.getShippingItemCode() :
            (erpConfig.getShippingItemCode() != null ? erpConfig.getShippingItemCode() : "SHIPPING");
        
        log.info("[배송비 품목 조회] tenantId={}, erpCode={}, itemCode={}", 
            order.getTenantId(), erpConfig.getErpCode(), shippingItemCode);
        
        Optional<ErpItem> shippingItem = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
            order.getTenantId(), erpConfig.getErpCode(), shippingItemCode
        );
        
        if (shippingItem.isPresent()) {
            ErpItem item = shippingItem.get();
            data.put("PROD_CD", item.getItemCode());
            data.put("PROD_DES", item.getItemName());
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
            log.info("[배송비 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), commonWhCd);
        } else {
            data.put("PROD_CD", shippingItemCode);
            data.put("PROD_DES", "배송비");
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
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
            Order order, String ioDate, Integer uploadSerNo, int index, ErpConfig erpConfig, Store store, String commonWhCd) {
        
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
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
            log.info("[수수료 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), commonWhCd);
        } else {
            data.put("PROD_CD", commissionItemCode);
            data.put("PROD_DES", erpConfig.getCommissionItemName() != null ? 
                erpConfig.getCommissionItemName() : "스마트스토어 수수료");
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
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
            Order order, String ioDate, Integer uploadSerNo, int index, 
            ErpConfig erpConfig, Store store, long shippingCommission, String commonWhCd) {
        
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
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
            log.info("[배송비 수수료 품목 조회 성공] itemCode={}, warehouseCode={}", 
                item.getItemCode(), commonWhCd);
        } else {
            data.put("PROD_CD", shippingCommissionItemCode);
            data.put("PROD_DES", erpConfig.getShippingCommissionItemName() != null ? 
                erpConfig.getShippingCommissionItemName() : "배송비 수수료");
            // 창고코드는 commonWhCd 사용 (통일)
            data.put("WH_CD", commonWhCd);
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

    /**
     * 정산 테이블에서 배송비 수수료 조회
     * settlement_type = 'SHIPPING_FEE'인 레코드의 commission_amount
     * 
     * @param order 주문 엔티티
     * @return 배송비 수수료 (없으면 null)
     */
    private Long getShippingFeeCommissionFromSettlement(Order order) {
        try {
            List<SettlementOrder> settlements = settlementOrderRepository
                .findByOrderIdAndSettlementType(order.getOrderId(), SettlementType.SHIPPING_FEE);
            
            if (settlements.isEmpty()) {
                log.debug("[배송비 수수료 없음] orderId={}, settlementType=SHIPPING_FEE", order.getOrderId());
                return null;
            }
            
            // 첫 번째 SHIPPING_FEE 타입 레코드의 commission_amount 사용
            SettlementOrder settlement = settlements.get(0);
            BigDecimal commission = settlement.getCommissionAmount();
            
            if (commission == null || commission.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("[배송비 수수료 0원] orderId={}, commission={}", order.getOrderId(), commission);
                return null;
            }
            
            long commissionValue = commission.longValue();
            log.info("[배송비 수수료 조회 성공] orderId={}, settlementType=SHIPPING_FEE, commission={}", 
                order.getOrderId(), commissionValue);
            
            return commissionValue;
        } catch (Exception e) {
            log.error("[배송비 수수료 조회 실패] orderId={}", order.getOrderId(), e);
            return null;
        }
    }

    /**
     * 당일 기준 정수형 시퀀스 생성
     * 
     * 규칙: orderId의 해시값을 기반으로 양수 정수 생성
     * (실제 운영에서는 Redis나 DB 시퀀스 사용 권장)
     * 
     * @param order 주문 엔티티
     * @return 정수형 시퀀스 번호
     */
    private Integer generateDailySequence(Order order) {
        // orderId의 해시값을 사용하여 양수 정수 생성
        int hash = Math.abs(order.getOrderId().hashCode());
        // 0 ~ 9999 범위로 제한 (SMALLINT 4자리)
        int sequence = hash % 10000;
        
        log.debug("[시퀀스 생성] orderId={}, sequence={} (4자리 제한)", order.getOrderId(), sequence);
        return sequence;
    }

    /**
     * 상품 전표의 창고코드 조회
     * 
     * 우선순위:
     * 1. ProductMapping의 warehouseCode
     * 2. ErpItem의 warehouseCode
     * 3. Store의 defaultWarehouseCode
     * 4. ErpConfig의 defaultWarehouseCode
     * 5. 기본값 "100"
     * 
     * @param order 주문 엔티티
     * @param erpConfig ERP 설정
     * @return 창고코드
     */
    private String getProductWarehouseCode(Order order, ErpConfig erpConfig) {
        // 첫 번째 아이템의 창고코드 조회
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            OrderItem firstItem = order.getItems().get(0);
            
            // ProductMapping에서 창고코드 조회
            Optional<ProductMappingResponse> mappingOpt = productMappingService.findActiveMapping(
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace(),
                firstItem.getMarketplaceProductId(),
                firstItem.getMarketplaceSku()
            );
            
            if (mappingOpt.isPresent()) {
                ProductMappingResponse mapping = mappingOpt.get();
                
                // 1순위: 매핑의 창고코드
                if (mapping.getWarehouseCode() != null && !mapping.getWarehouseCode().isEmpty()) {
                    log.info("[창고코드 조회] source=ProductMapping, code={}", mapping.getWarehouseCode());
                    return mapping.getWarehouseCode();
                }
                
                // 2순위: ERP 품목의 창고코드
                String erpItemCode = mapping.getErpItemCode();
                Optional<ErpItem> erpItemOpt = erpItemRepository.findByTenantIdAndErpCodeAndItemCode(
                    order.getTenantId(), erpConfig.getErpCode(), erpItemCode
                );
                
                if (erpItemOpt.isPresent() && erpItemOpt.get().getWarehouseCode() != null) {
                    log.info("[창고코드 조회] source=ErpItem, code={}", erpItemOpt.get().getWarehouseCode());
                    return erpItemOpt.get().getWarehouseCode();
                }
            }
        }
        
        // 3순위: Store의 defaultWarehouseCode
        Store store = storeRepository.findById(order.getStoreId()).orElse(null);
        if (store != null && store.getDefaultWarehouseCode() != null && !store.getDefaultWarehouseCode().isEmpty()) {
            log.info("[창고코드 조회] source=Store.defaultWarehouseCode, code={}", store.getDefaultWarehouseCode());
            return store.getDefaultWarehouseCode();
        }
        
        // 4순위: ErpConfig의 defaultWarehouseCode
        if (erpConfig.getDefaultWarehouseCode() != null && !erpConfig.getDefaultWarehouseCode().isEmpty()) {
            log.info("[창고코드 조회] source=ErpConfig.defaultWarehouseCode, code={}", erpConfig.getDefaultWarehouseCode());
            return erpConfig.getDefaultWarehouseCode();
        }
        
        // 5순위: 기본값
        log.warn("[창고코드 조회] source=default, code=100");
        return "100";
    }
}
