package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.erp.entity.ErpConfig;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.repository.ErpConfigRepository;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
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
import com.sellsync.api.domain.settlement.entity.SettlementOrderItem;
import com.sellsync.api.domain.settlement.repository.SettlementOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final TemplateBasedPostingBuilder templateBasedPostingBuilder;

    private static final DateTimeFormatter IO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 정산 수집된 주문에 대한 전표 생성 (한 셋트로 통합)
     * 
     * 네이버 스마트스토어의 경우 하나의 bundleOrderId에 여러 개의 productOrderId가 있을 수 있으며,
     * 이들을 하나의 전표로 묶어서 생성합니다.
     * 
     * @param bundleOrderId 번들 주문 ID (네이버 스마트스토어의 경우 묶음 주문번호)
     * @param erpCode ERP 코드 (ECOUNT 등)
     * @return 생성된 전표 (1개만 생성됨)
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws IllegalStateException settlement_status가 COLLECTED가 아닌 경우
     */
    @Transactional
    public PostingResponse createPostingsForSettledOrder(String bundleOrderId, String erpCode) {
        log.info("[정산 전표 생성 시작] bundleOrderId={}, erpCode={}", bundleOrderId, erpCode);

        // 1. 주문 조회 (bundleOrderId로 조회 - 여러 개 주문이 나올 수 있음)
        List<Order> allOrders = orderRepository.findByBundleOrderId(bundleOrderId);

        if (allOrders.isEmpty()) {
            throw new OrderNotFoundException(UUID.randomUUID()); // bundleOrderId는 String이라 임시로 UUID 생성
        }

        // 전표 생성 대상: SHIPPING 또는 DELIVERED 상태 주문만 포함
        // NEW, CONFIRMED 등 아직 출고 전 상태의 주문은 전표 생성에서 제외
        List<Order> orders = allOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.SHIPPING || o.getOrderStatus() == OrderStatus.DELIVERED)
                .toList();

        if (orders.isEmpty()) {
            log.warn("[전표 생성 스킵 - 출고 전 주문만 존재] bundleOrderId={}, 전체 주문={}건, 상태별: {}",
                bundleOrderId, allOrders.size(),
                allOrders.stream().collect(java.util.stream.Collectors.groupingBy(
                    o -> o.getOrderStatus().name(), java.util.stream.Collectors.counting())));
            throw new IllegalStateException(
                String.format("전표 생성 대상 주문이 없습니다 (SHIPPING/DELIVERED 상태만 전표 생성 가능). bundleOrderId=%s", bundleOrderId));
        }

        log.info("[번들 주문 조회 완료] bundleOrderId={}, 전체={}건, 전표대상(SHIPPING/DELIVERED)={}건",
            bundleOrderId, allOrders.size(), orders.size());

        // 2. settlement_status 확인 (쿠팡은 정산 미수집도 허용)
        Order firstOrderForCheck = orders.get(0);
        boolean isCoupang = firstOrderForCheck.getMarketplace() == Marketplace.COUPANG;

        if (!isCoupang) {
            // 쿠팡 외 마켓플레이스: 모든 주문이 COLLECTED여야 함
            List<Order> notCollectedOrders = orders.stream()
                    .filter(o -> o.getSettlementStatus() != SettlementCollectionStatus.COLLECTED)
                    .toList();

            if (!notCollectedOrders.isEmpty()) {
                String errorMsg = String.format(
                    "일부 주문의 정산 상태가 COLLECTED가 아닙니다. bundleOrderId=%s, notCollected count=%d",
                    bundleOrderId, notCollectedOrders.size()
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        } else {
            log.info("[쿠팡 주문 - 정산 미수집 허용] bundleOrderId={}, settlementStatus={}",
                bundleOrderId, firstOrderForCheck.getSettlementStatus());
        }

        // 3. 상품 매핑 완료 확인 (모든 주문의 모든 상품이 매핑되어야 함)
        List<String> unmappedItems = new ArrayList<>();
        for (Order order : orders) {
            unmappedItems.addAll(checkProductMappings(order));
        }
        
        if (!unmappedItems.isEmpty()) {
            String errorMsg = String.format(
                "상품 매핑이 완료되지 않은 항목이 있습니다. 매핑 관리 화면에서 먼저 매핑을 완료해주세요. bundleOrderId=%s, unmapped items=%s",
                bundleOrderId, unmappedItems
            );
            log.error("[전표 생성 차단 - 상품매핑 미완료] bundleOrderId={}, unmappedItems={}", bundleOrderId, unmappedItems);
            throw new IllegalStateException(errorMsg);
        }

        // 4. 대표 주문 정보 (첫 번째 주문 기준)
        Order firstOrder = orders.get(0);

        // 5. ERP 설정 조회 (수수료 품목 코드 확인용)
        ErpConfig erpConfig = erpConfigRepository.findByTenantIdAndErpCode(firstOrder.getTenantId(), erpCode)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("ERP 설정이 없습니다. tenantId=%s, erpCode=%s", firstOrder.getTenantId(), erpCode)
                ));

        // 6. Store 조회 (거래처 코드 확인용)
        Store store = storeRepository.findById(firstOrder.getStoreId())
                .orElseThrow(() -> new IllegalStateException(
                    String.format("스토어를 찾을 수 없습니다. storeId=%s", firstOrder.getStoreId())
                ));

        // 7. 통합 전표 Payload 생성 (여러 주문의 상품판매 + 배송비 + 수수료)
        String payload = buildIntegratedPostingPayload(orders, erpConfig, store);

        // 8. 전표 생성 (PostingType은 PRODUCT_SALES로 대표)
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(firstOrder.getTenantId())
                .erpCode(erpCode)
                .orderId(firstOrder.getOrderId())
                .marketplace(firstOrder.getMarketplace())
                .marketplaceOrderId(bundleOrderId) // bundleOrderId를 대표 주문번호로 사용
                .postingType(PostingType.PRODUCT_SALES) // 통합 전표의 대표 타입
                .requestPayload(payload)
                .build();

        PostingResponse posting = postingService.createOrGet(request);
        log.info("[통합 전표 생성] postingId={}", posting.getPostingId());

        // 9. 정산 전표 생성 완료 마킹
        // 실제 정산 데이터가 수집된(COLLECTED) 주문만 POSTED로 변경
        // 정산 미수집(NOT_COLLECTED) 상태에서 예상 수수료로 전표를 먼저 생성한 경우,
        // 상태를 유지하여 차후 실제 정산 데이터 수집 시 금액 비교가 가능하도록 함
        int postedCount = 0;
        int skippedCount = 0;
        for (Order order : orders) {
            if (order.getSettlementStatus() == SettlementCollectionStatus.COLLECTED) {
                order.markSettlementPosted();
                postedCount++;
            } else {
                skippedCount++;
                log.info("[정산 미수집 주문 - 상태 유지] orderId={}, settlementStatus={}, expectedSettlementAmount={}",
                    order.getMarketplaceOrderId(), order.getSettlementStatus(), order.getExpectedSettlementAmount());
            }
        }
        orderRepository.saveAll(orders);
        log.info("[정산 전표 생성 완료 마킹] bundleOrderId={}, POSTED={}건, 상태유지={}건",
            bundleOrderId, postedCount, skippedCount);

        log.info("[정산 전표 생성 완료] bundleOrderId={}, postingId={}", bundleOrderId, posting.getPostingId());

        return posting;
    }

    // ========== Private Helper Methods ==========

    /**
     * 통합 전표 Payload 생성 (여러 주문을 하나의 전표로 통합)
     * 
     * SaleList 구조:
     * - [주문별] 상품판매 전표 (N개)
     * - [주문별] 상품수수료 전표 (N개)
     * - [번들 1건] 배송비 전표 (1개) - 대표 주문 기준
     * - [번들 1건] 배송비수수료 전표 (1개) - 대표 주문 기준
     */
    private String buildIntegratedPostingPayload(List<Order> orders, ErpConfig erpConfig, Store store) {
        log.info("[통합 Payload 생성 시작] bundleOrderId={}, 주문 개수={}", 
            orders.get(0).getBundleOrderId(), orders.size());

        Order firstOrder = orders.get(0);  // 대표 주문 (배송비/배송수수료 기준)
        List<Map<String, Object>> saleList = new ArrayList<>();
        String ioDate = firstOrder.getPaidAt().format(IO_DATE_FORMATTER);
        
        Integer uploadSerNo = generateDailySequence(firstOrder);
        String commonWhCd = getProductWarehouseCode(firstOrder, erpConfig);

        int index = 0;

        // ========== 1. 각 주문별 상품판매 전표 ==========
        for (Order order : orders) {
            if (order.getTotalProductAmount() != null && order.getTotalProductAmount() > 0) {
                Map<String, Object> productSalesData = templateBasedPostingBuilder.buildBulkData(
                    order, store, erpConfig.getErpCode(), PostingType.PRODUCT_SALES, "product_sales"
                );
                productSalesData.put("UPLOAD_SER_NO", uploadSerNo);
                productSalesData.put("IO_DATE", ioDate);
                productSalesData.put("WH_CD", commonWhCd);
                
                saleList.add(Collections.singletonMap("BulkDatas", productSalesData));
                log.info("[상품판매 전표 추가] orderId={}, index={}, amount={}", 
                    order.getOrderId(), ++index, order.getTotalProductAmount());
            }
        }

        // ========== 2. 각 주문별 상품수수료 전표 ==========
        for (Order order : orders) {
            Long productCommission = getProductCommissionFromSettlement(order);

            // 폴백: 정산 데이터 없으면 Order 엔티티의 예상 수수료 사용 (쿠팡)
            if (productCommission == null && order.getCommissionAmount() != null && order.getCommissionAmount() > 0) {
                productCommission = order.getCommissionAmount();
                log.info("[상품수수료 - 예상 수수료 사용] orderId={}, commission={}",
                    order.getOrderId(), productCommission);
            }

            if (productCommission != null && productCommission > 0
                    && store.getCommissionItemCode() != null 
                    && !store.getCommissionItemCode().isEmpty()) {
                
                order.setCommissionAmount(productCommission);
                
                Map<String, Object> commissionData = templateBasedPostingBuilder.buildBulkData(
                    order, store, erpConfig.getErpCode(), PostingType.PRODUCT_SALES, "product_commission"
                );
                commissionData.put("UPLOAD_SER_NO", uploadSerNo);
                commissionData.put("IO_DATE", ioDate);
                commissionData.put("WH_CD", commonWhCd);
                
                saleList.add(Collections.singletonMap("BulkDatas", commissionData));
                log.info("[상품수수료 전표 추가] orderId={}, index={}, amount={}", 
                    order.getOrderId(), ++index, productCommission);
            }
        }

        // ========== 3. 배송비 전표 (번들 당 1건 - 대표 주문 기준) ==========
        Long shippingAmount = firstOrder.getTotalShippingAmount();
        if (shippingAmount == null || shippingAmount == 0) {
            shippingAmount = firstOrder.getShippingFee();
        }
        
        if (shippingAmount != null && shippingAmount > 0 
                && store.getShippingItemCode() != null 
                && !store.getShippingItemCode().isEmpty()) {
            
            Map<String, Object> shippingData = templateBasedPostingBuilder.buildBulkData(
                firstOrder, store, erpConfig.getErpCode(), PostingType.PRODUCT_SALES, "product_shipping"
            );
            shippingData.put("UPLOAD_SER_NO", uploadSerNo);
            shippingData.put("IO_DATE", ioDate);
            shippingData.put("WH_CD", commonWhCd);
            
            saleList.add(Collections.singletonMap("BulkDatas", shippingData));
            log.info("[배송비 전표 추가 - 번들 1건] bundleOrderId={}, index={}, amount={}", 
                firstOrder.getBundleOrderId(), ++index, shippingAmount);
        }

        // ========== 4. 배송비수수료 전표 (번들 당 1건 - 대표 주문 기준) ==========
        // Order.shippingCommissionAmount 사용, 0이면 번들 내 다른 주문에서 조회
        Long shippingCommission = getShippingCommissionFromOrderOrBundle(firstOrder);
        
        log.info("[배송비수수료 전표 생성 조건 체크] bundleOrderId={}, shippingAmount={}, shippingCommission={}, " +
                "shippingCommissionItemCode={}", 
            firstOrder.getBundleOrderId(), 
            shippingAmount, 
            shippingCommission,
            store.getShippingCommissionItemCode());
        
        if (shippingAmount != null && shippingAmount > 0 
                && shippingCommission != null && shippingCommission > 0
                && store.getShippingCommissionItemCode() != null 
                && !store.getShippingCommissionItemCode().isEmpty()) {
            
            firstOrder.setShippingCommissionAmount(shippingCommission);
            
            Map<String, Object> shippingCommissionData = templateBasedPostingBuilder.buildBulkData(
                firstOrder, store, erpConfig.getErpCode(), PostingType.PRODUCT_SALES, "product_shipping_commission"
            );
            shippingCommissionData.put("UPLOAD_SER_NO", uploadSerNo);
            shippingCommissionData.put("IO_DATE", ioDate);
            shippingCommissionData.put("WH_CD", commonWhCd);
            
            saleList.add(Collections.singletonMap("BulkDatas", shippingCommissionData));
            log.info("[배송비수수료 전표 추가 - 번들 1건] bundleOrderId={}, index={}, amount={}", 
                firstOrder.getBundleOrderId(), ++index, shippingCommission);
        } else {
            log.warn("[배송비수수료 전표 생성 스킵] bundleOrderId={}, 조건 불충족 - " +
                    "shippingAmount={}, shippingCommission={}, shippingCommissionItemCode={}", 
                firstOrder.getBundleOrderId(),
                shippingAmount,
                shippingCommission,
                store.getShippingCommissionItemCode());
        }

        log.info("[통합 Payload 생성 최종] bundleOrderId={}, 주문 개수={}, 총 BulkDatas 개수={}", 
            firstOrder.getBundleOrderId(), orders.size(), saleList.size());

        // JSON 직렬화
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("SaleList", saleList);
            
            String jsonResult = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            log.info("[통합 Payload JSON 생성 완료] bundleOrderId={}, JSON length={}", 
                firstOrder.getBundleOrderId(), jsonResult.length());
            
            return jsonResult;
        } catch (Exception e) {
            log.error("[통합 Payload 생성 실패] bundleOrderId={}", firstOrder.getBundleOrderId(), e);
            throw new RuntimeException("통합 전표 Payload 생성 실패", e);
        }
    }


    /**
     * 정산 테이블에서 배송비 수수료 조회 (bundleOrderId 기준)
     * 
     * DELIVERY 타입의 정산 데이터는 별도 productOrderId를 가지므로
     * orderId가 아닌 bundleOrderId로 조회해야 함
     * 
     * @param order 주문 엔티티
     * @return 배송비 수수료 (없으면 null)
     */
    private Long getShippingFeeCommissionFromSettlement(Order order) {
        try {
            String bundleOrderId = order.getBundleOrderId();
            
            // bundleOrderId가 없으면 orderId로 폴백
            if (bundleOrderId == null || bundleOrderId.isEmpty()) {
                log.debug("[배송비 수수료 - bundleOrderId 없음, orderId로 조회] orderId={}", order.getOrderId());
                List<SettlementOrder> settlements = settlementOrderRepository.findByOrderId(order.getOrderId());
                return extractShippingCommissionFromSettlements(settlements, order);
            }
            
            // bundleOrderId로 정산 데이터 조회 (DELIVERY 타입 포함)
            log.info("[배송비 수수료 조회 시작 - bundleOrderId 기준] bundleOrderId={}", bundleOrderId);
            List<SettlementOrder> settlements = settlementOrderRepository.findByBundleOrderIdWithItems(bundleOrderId);
            
            if (settlements.isEmpty()) {
                log.debug("[정산 데이터 없음 - bundleOrderId 기준] bundleOrderId={}", bundleOrderId);
                return null;
            }
            
            log.info("[정산 데이터 조회 완료] bundleOrderId={}, 정산건수={}", bundleOrderId, settlements.size());
            
            return extractShippingCommissionFromSettlements(settlements, order);
            
        } catch (Exception e) {
            log.error("[배송비 수수료 조회 실패] orderId={}, bundleOrderId={}", 
                order.getOrderId(), order.getBundleOrderId(), e);
            return null;
        }
    }

    /**
     * 정산 데이터에서 배송비 수수료 추출
     * DELIVERY 타입의 items만 필터링하여 수수료 합산
     */
    private Long extractShippingCommissionFromSettlements(List<SettlementOrder> settlements, Order order) {
        if (settlements == null || settlements.isEmpty()) {
            return null;
        }
        
        // 모든 정산 주문의 items를 로깅
        for (SettlementOrder so : settlements) {
            log.debug("[정산 주문 items 확인] settlementOrderId={}, bundleOrderId={}, itemCount={}", 
                so.getSettlementOrderId(), so.getBundleOrderId(), 
                so.getItems() != null ? so.getItems().size() : 0);
            
            if (so.getItems() != null) {
                for (SettlementOrderItem item : so.getItems()) {
                    log.debug("[정산 아이템] productOrderType={}, totalPayCommissionAmount={}", 
                        item.getProductOrderType(), item.getTotalPayCommissionAmount());
                }
            }
        }
        
        // 배송비(DELIVERY) 타입의 items만 필터링하여 수수료 합산
        BigDecimal commission = settlements.stream()
            .flatMap(so -> so.getItems().stream())
            .filter(item -> "DELIVERY".equals(item.getProductOrderType()))
            .map(SettlementOrderItem::getTotalPayCommissionAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("[DELIVERY 타입 수수료 합산 결과] orderId={}, commission={}", order.getOrderId(), commission);
        
        if (commission.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("[배송비 수수료 0원] orderId={}", order.getOrderId());
            return null;
        }
        
        // 음수 값 검증 (배송비 수수료는 마이너스여야 함)
        if (commission.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("[배송비 수수료 양수 값 발견] orderId={}, commission={} - 음수로 변환하지 않음 (데이터 확인 필요)", 
                order.getOrderId(), commission);
        }
        
        long commissionValue = Math.abs(commission.longValue());
        log.info("[배송비 수수료 조회 성공] orderId={}, bundleOrderId={}, commission={}", 
            order.getOrderId(), order.getBundleOrderId(), commissionValue);
        
        return commissionValue;
    }

    /**
     * 주문에서 배송비 수수료 조회
     * 
     * 번들 주문의 경우, 배송비 수수료가 대표 주문 1개에만 저장되어 있을 수 있음.
     * 현재 주문에 배송비 수수료가 없으면, 같은 번들의 다른 주문에서 조회.
     * 
     * @param order 현재 주문
     * @return 배송비 수수료 (없으면 null)
     */
    private Long getShippingCommissionFromOrderOrBundle(Order order) {
        // 1. 현재 주문에서 배송비 수수료 조회
        Long shippingCommission = order.getShippingCommissionAmount();
        
        if (shippingCommission != null && shippingCommission > 0) {
            log.info("[배송비 수수료 - 현재 주문에서 조회 성공] orderId={}, shippingCommission={}", 
                order.getOrderId(), shippingCommission);
            return shippingCommission;
        }
        
        // 2. 현재 주문에 없으면, 같은 번들의 다른 주문에서 조회
        String bundleOrderId = order.getBundleOrderId();
        if (bundleOrderId == null || bundleOrderId.isEmpty()) {
            log.debug("[배송비 수수료 - bundleOrderId 없음] orderId={}", order.getOrderId());
            return null;
        }
        
        log.info("[배송비 수수료 - 번들 내 다른 주문에서 조회 시도] orderId={}, bundleOrderId={}", 
            order.getOrderId(), bundleOrderId);
        
        // 같은 번들의 모든 주문 조회
        List<Order> bundleOrders = orderRepository.findByTenantIdAndBundleOrderIdIn(
            order.getTenantId(), 
            Collections.singletonList(bundleOrderId)
        );
        
        // 배송비 수수료가 있는 주문 찾기
        for (Order bundleOrder : bundleOrders) {
            Long bundleShippingCommission = bundleOrder.getShippingCommissionAmount();
            if (bundleShippingCommission != null && bundleShippingCommission > 0) {
                log.info("[배송비 수수료 - 번들 내 다른 주문에서 조회 성공] " +
                    "currentOrderId={}, foundOrderId={}, shippingCommission={}", 
                    order.getOrderId(), bundleOrder.getOrderId(), bundleShippingCommission);
                return bundleShippingCommission;
            }
        }
        
        log.debug("[배송비 수수료 - 번들 내 모든 주문에서 찾지 못함] orderId={}, bundleOrderId={}, bundleOrderCount={}", 
            order.getOrderId(), bundleOrderId, bundleOrders.size());
        return null;
    }

    /**
     * 정산 테이블에서 상품 수수료 조회
     * 
     * ⚠️ 중요: marketplaceOrderId 기준으로 조회
     * 같은 marketplaceOrderId로 여러 정산 레코드가 있을 수 있으므로
     * orderId가 아닌 marketplaceOrderId로 조회해야 함
     * 
     * @param order 주문 엔티티
     * @return 상품 수수료 (없으면 null)
     */
    private Long getProductCommissionFromSettlement(Order order) {
        try {
            // ✅ marketplaceOrderId로 정산 데이터 조회 (orderId 대신)
            List<SettlementOrder> settlements = settlementOrderRepository
                .findByMarketplaceOrderIdWithItems(order.getMarketplaceOrderId());
            
            if (settlements.isEmpty()) {
                log.debug("[정산 데이터 없음] marketplaceOrderId={}", order.getMarketplaceOrderId());
                return null;
            }
            
            log.debug("[정산 데이터 조회 완료] marketplaceOrderId={}, 정산건수={}", 
                order.getMarketplaceOrderId(), settlements.size());
            
            // 상품(DELIVERY가 아닌) 타입의 items만 필터링하여 수수료 합산
            BigDecimal commission = settlements.stream()
                .flatMap(so -> so.getItems().stream())
                .filter(item -> !"DELIVERY".equals(item.getProductOrderType()))
                .map(SettlementOrderItem::calculateTotalCommission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (commission == null || commission.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("[상품 수수료 0원] marketplaceOrderId={}, commission={}", 
                    order.getMarketplaceOrderId(), commission);
                return null;
            }
            
            // 음수 값 검증 (상품 수수료는 마이너스여야 함)
            if (commission.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("[상품 수수료 양수 값 발견] marketplaceOrderId={}, commission={} - 음수로 변환하지 않음 (데이터 확인 필요)", 
                    order.getMarketplaceOrderId(), commission);
            }
            
            long commissionValue = Math.abs(commission.longValue()); // 절대값으로 변환 (createCommissionBulkData에서 다시 음수로 만듦)
            log.info("[상품 수수료 조회 성공] marketplaceOrderId={}, commission={}", 
                order.getMarketplaceOrderId(), commissionValue);
            
            return commissionValue;
        } catch (Exception e) {
            log.error("[상품 수수료 조회 실패] marketplaceOrderId={}", order.getMarketplaceOrderId(), e);
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

    /**
     * OrderItem의 ProductMapping 확인
     * 
     * mapping_status = MAPPED이고 isActive = true인 매핑만 유효하다고 판단
     * 
     * @param order 주문 엔티티
     * @return 매핑되지 않은 상품 목록 (productId:sku 형식)
     */
    private List<String> checkProductMappings(Order order) {
        List<String> unmappedItems = new ArrayList<>();

        if (order.getItems() == null || order.getItems().isEmpty()) {
            log.warn("[상품 아이템 없음] orderId={}", order.getOrderId());
            return unmappedItems;
        }

        log.info("[매핑 체크 시작] orderId={}, tenantId={}, storeId={}, marketplace={}, itemCount={}", 
            order.getOrderId(), order.getTenantId(), order.getStoreId(), order.getMarketplace(), order.getItems().size());

        for (OrderItem item : order.getItems()) {
            log.info("[매핑 조회 시도] orderId={}, productId={}, sku={}, tenantId={}, storeId={}, marketplace={}", 
                order.getOrderId(), 
                item.getMarketplaceProductId(), 
                item.getMarketplaceSku(),
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace());

            Optional<ProductMappingResponse> mapping = productMappingService.findActiveMapping(
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace(),
                item.getMarketplaceProductId(),
                item.getMarketplaceSku()
            );

            if (mapping.isEmpty()) {
                String itemKey = String.format("%s:%s", 
                    item.getMarketplaceProductId(), item.getMarketplaceSku());
                unmappedItems.add(itemKey);
                log.error("[상품 매핑 없음 또는 조건 불충족] orderId={}, productId={}, sku={}, tenantId={}, storeId={}, marketplace={} " +
                    "→ DB에서 매핑 데이터를 직접 확인해주세요. " +
                    "확인사항: (1) isActive=true 인지, (2) mappingStatus=MAPPED 인지, (3) productId/sku 값이 정확한지", 
                    order.getOrderId(), 
                    item.getMarketplaceProductId(), 
                    item.getMarketplaceSku(),
                    order.getTenantId(),
                    order.getStoreId(),
                    order.getMarketplace());
            } else {
                log.info("[매핑 조회 성공] orderId={}, productId={}, sku={}, erpItemCode={}", 
                    order.getOrderId(), 
                    item.getMarketplaceProductId(), 
                    item.getMarketplaceSku(),
                    mapping.get().getErpItemCode());
            }
        }

        return unmappedItems;
    }
}
