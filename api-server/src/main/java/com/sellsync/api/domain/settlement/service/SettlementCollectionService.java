package com.sellsync.api.domain.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.settlement.adapter.MarketplaceSettlementClient;
import com.sellsync.api.domain.settlement.dto.SettlementCollectionResult;
import com.sellsync.api.domain.settlement.dto.smartstore.DailySettlementElement;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.entity.SettlementOrderItem;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import com.sellsync.api.domain.settlement.repository.SettlementOrderItemRepository;
import com.sellsync.api.domain.settlement.repository.SettlementOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 정산 수집 서비스 (리팩토링 버전)
 * 
 * 역할:
 * - 마켓 정산 API 연동
 * - 주문별 정산 내역 수집 및 처리
 * - 주문 테이블에 수수료 정보 업데이트
 * - 정산 배치 및 정산 주문 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCollectionService {

    private final Map<String, MarketplaceSettlementClient> marketplaceSettlementClients;
    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementOrderRepository settlementOrderRepository;
    private final SettlementOrderItemRepository settlementOrderItemRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    /**
     * 정산 데이터 수집 및 벌크 처리 (메인 메서드)
     * 
     * 플로우:
     * 1. 마켓 API에서 정산 데이터 수집
     * 2. orderId로 기존 주문 매칭
     * 3. 주문 테이블에 수수료 정보 벌크 업데이트
     * 4. SettlementBatch 벌크 UPSERT
     * 5. SettlementOrder 벌크 UPSERT
     */
    @Transactional
    public SettlementCollectionResult collectAndProcessSettlements(
            UUID tenantId,
            UUID storeId,
            Marketplace marketplace,
            LocalDate startDate,
            LocalDate endDate,
            String credentials) {
        
        log.info("[정산 수집 시작] tenantId={}, storeId={}, marketplace={}, period={} ~ {}", 
            tenantId, storeId, marketplace, startDate, endDate);

        // ✅ 주문 수집 상태 사전 체크 (경고용)
        long orderCount = orderRepository.countByStoreIdAndPaidAtBetween(
                storeId, 
                startDate.atStartOfDay(), 
                endDate.plusDays(1).atStartOfDay()
        );
        log.info("[정산 수집] 해당 기간 주문 수: {} 건 ({}~{})", orderCount, startDate, endDate);
        
        if (orderCount == 0) {
            log.warn("[정산 수집] ⚠️ 해당 기간에 수집된 주문이 없습니다. 주문 수집을 먼저 실행해주세요.");
        }

        // 1. 마켓 API에서 정산 데이터 수집
        MarketplaceSettlementClient client = getSettlementClient(marketplace.name());
        List<DailySettlementElement> elements = client.fetchSettlementElements(startDate, endDate, credentials);
        
        log.info("[정산 데이터 수집 완료] count={}", elements.size());
        
        if (elements.isEmpty()) {
            return SettlementCollectionResult.empty();
        }

        // 2. productOrderId(=marketplaceOrderId)로 기존 주문 조회 (벌크)
        // ⚠️ 정산 API 데이터 구조:
        //    - orderId: bundle_order_id (묶음 주문, 중복 가능)
        //    - productOrderId: marketplace_order_id (개별 상품 주문, 고유함)
        // ⚠️ 정산은 상품별로 오므로 productOrderId로 매칭해야 함
        // ⚠️ storeId로 필터링하여 동일 테넌트 내 다른 스토어의 주문과 충돌 방지
        List<String> marketplaceOrderIds = elements.stream()
                .map(DailySettlementElement::getProductOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        
        Map<String, Order> orderMap = orderRepository
                .findByStoreIdAndMarketplaceOrderIdIn(storeId, marketplaceOrderIds)
                .stream()
                .collect(Collectors.toMap(Order::getMarketplaceOrderId, o -> o));
        
        log.info("[주문 매칭 완료] 요청={}, 매칭={}", marketplaceOrderIds.size(), orderMap.size());

        // 3. 주문 테이블에 수수료 정보 벌크 업데이트
        int updatedOrders = bulkUpdateOrderSettlementInfo(tenantId, storeId, elements, orderMap, startDate);
        log.info("[주문 수수료 업데이트 완료] count={}", updatedOrders);

        // 4. SettlementBatch 생성/업데이트 (일별 배치)
        Map<String, SettlementBatch> batchMap = createOrUpdateBatches(
            tenantId, storeId, marketplace, elements, startDate, endDate
        );
        log.info("[정산 배치 처리 완료] count={}", batchMap.size());

        // 5. SettlementOrder 벌크 UPSERT
        int createdOrders = bulkUpsertSettlementOrders(tenantId, marketplace, elements, orderMap, batchMap);
        log.info("[정산 주문 생성 완료] count={}", createdOrders);

        return SettlementCollectionResult.builder()
                .totalElements(elements.size())
                .matchedOrders(orderMap.size())
                .updatedOrders(updatedOrders)
                .createdBatches(batchMap.size())
                .createdSettlementOrders(createdOrders)
                .build();
    }

    /**
     * 주문 테이블에 정산 정보 벌크 업데이트
     * 
     * 주의: 한 주문에 대해 여러 element가 올 수 있음
     * - productOrderType != "DELIVERY": 상품 주문 (상품 수수료) → marketplaceOrderId로 매칭
     * - productOrderType = "DELIVERY": 배송비 (배송비 수수료) → bundleOrderId로 매칭
     * 
     * ⚠️ 정산 API 데이터 구조:
     * - orderId: bundle_order_id (묶음 주문)
     * - productOrderId: marketplace_order_id (개별 상품 주문)
     * - DELIVERY 타입의 productOrderId는 주문 테이블에 없음 → orderId(bundleOrderId)로 매칭 필요
     * 
     * ⚠️ 성능 최적화: timeout 방지를 위해 500건씩 청크로 나누어 처리
     */
    private int bulkUpdateOrderSettlementInfo(
            UUID tenantId,
            UUID storeId,
            List<DailySettlementElement> elements,
            Map<String, Order> orderMap,
            LocalDate settlementDate) {
        
        // ========== 1. 상품 수수료 처리 (DELIVERY가 아닌 타입 - marketplaceOrderId로 매칭) ==========
        List<DailySettlementElement> productElements = elements.stream()
                .filter(e -> !"DELIVERY".equals(e.getProductOrderType()))
                .filter(e -> e.getProductOrderId() != null && orderMap.containsKey(e.getProductOrderId()))
                .toList();
        
        Map<String, OrderSettlementData> productSettlementMap = new java.util.HashMap<>();
        int productTypeCount = 0;
        
        for (DailySettlementElement e : productElements) {
            String marketplaceOrderId = e.getProductOrderId();  // marketplace_order_id
            OrderSettlementData data = productSettlementMap.computeIfAbsent(marketplaceOrderId, k -> new OrderSettlementData());
            
            data.productCommission += e.getTotalCommission();
            data.productSettlement += e.getCalculatedSettleAmount();
            productTypeCount++;
        }
        
        // ========== 2. 배송비 수수료 처리 (DELIVERY 타입 - bundleOrderId로 매칭) ==========
        List<DailySettlementElement> deliveryElements = elements.stream()
                .filter(e -> "DELIVERY".equals(e.getProductOrderType()))
                .filter(e -> e.getOrderId() != null)  // orderId = bundleOrderId
                .toList();
        
        // bundleOrderId로 주문 조회 (대표 주문 선택용)
        List<String> bundleOrderIds = deliveryElements.stream()
                .map(DailySettlementElement::getOrderId)
                .distinct()
                .toList();
        
        Map<String, Order> bundleOrderMap = new java.util.HashMap<>();
        if (!bundleOrderIds.isEmpty()) {
            List<Order> bundleOrders = orderRepository.findByStoreIdAndBundleOrderIdIn(storeId, bundleOrderIds);
            
            // bundleOrderId별 대표 주문 (첫 번째 주문 또는 배송비가 있는 주문)
            for (Order order : bundleOrders) {
                String bundleId = order.getBundleOrderId();
                if (!bundleOrderMap.containsKey(bundleId)) {
                    bundleOrderMap.put(bundleId, order);
                } else {
                    // 배송비가 있는 주문을 우선 선택
                    Order existing = bundleOrderMap.get(bundleId);
                    if (existing.getTotalShippingAmount() == null || existing.getTotalShippingAmount() == 0) {
                        if (order.getTotalShippingAmount() != null && order.getTotalShippingAmount() > 0) {
                            bundleOrderMap.put(bundleId, order);
                        }
                    }
                }
            }
        }
        
        // bundleOrderId별 배송비 수수료 집계
        Map<String, Long> shippingCommissionByBundle = new java.util.HashMap<>();
        int deliveryTypeCount = 0;
        long totalShippingCommission = 0L;
        
        for (DailySettlementElement e : deliveryElements) {
            String bundleOrderId = e.getOrderId();  // orderId = bundleOrderId
            long commission = e.getTotalCommission();
            
            Long existing = shippingCommissionByBundle.get(bundleOrderId);
            shippingCommissionByBundle.put(bundleOrderId, (existing != null ? existing : 0L) + commission);
            deliveryTypeCount++;
            totalShippingCommission += commission;
            
            log.debug("[정산 수집] 배송비 타입 발견 - bundleOrderId={}, commission={}, settleAmount={}", 
                    bundleOrderId, commission, e.getCalculatedSettleAmount());
        }
        
        // 대표 주문의 marketplaceOrderId로 배송비 수수료 매핑
        Map<String, OrderSettlementData> deliverySettlementMap = new java.util.HashMap<>();
        for (Map.Entry<String, Long> entry : shippingCommissionByBundle.entrySet()) {
            String bundleOrderId = entry.getKey();
            Long shippingCommission = entry.getValue();
            
            Order representativeOrder = bundleOrderMap.get(bundleOrderId);
            if (representativeOrder != null) {
                String marketplaceOrderId = representativeOrder.getMarketplaceOrderId();
                OrderSettlementData data = deliverySettlementMap.computeIfAbsent(marketplaceOrderId, k -> new OrderSettlementData());
                data.shippingCommission = shippingCommission;
                
                log.info("[배송비 수수료 매핑] bundleOrderId={}, representativeOrderId={}, marketplaceOrderId={}, shippingCommission={}", 
                        bundleOrderId, representativeOrder.getOrderId(), marketplaceOrderId, shippingCommission);
            } else {
                log.warn("[배송비 수수료 매칭 실패] bundleOrderId={}, 대표 주문을 찾을 수 없음", bundleOrderId);
            }
        }
        
        log.info("[정산 수집] 타입별 집계 - 상품: {} 건, 배송비: {} 건, 배송비 수수료 합계: {} 원", 
                productTypeCount, deliveryTypeCount, totalShippingCommission);
        
        // ========== 3. 상품 수수료와 배송비 수수료 병합 ==========
        Map<String, OrderSettlementData> mergedSettlementMap = new java.util.HashMap<>(productSettlementMap);
        for (Map.Entry<String, OrderSettlementData> entry : deliverySettlementMap.entrySet()) {
            String marketplaceOrderId = entry.getKey();
            OrderSettlementData deliveryData = entry.getValue();
            
            OrderSettlementData data = mergedSettlementMap.computeIfAbsent(marketplaceOrderId, k -> new OrderSettlementData());
            data.shippingCommission = deliveryData.shippingCommission;
        }
        
        if (mergedSettlementMap.isEmpty()) {
            log.warn("[정산 정보 업데이트] 업데이트할 데이터가 없습니다.");
            return 0;
        }
        
        // ========== 4. 벌크 업데이트 실행 (청크 처리) ==========
        final int CHUNK_SIZE = 500;
        int totalSize = mergedSettlementMap.size();
        int totalUpdated = 0;
        
        log.info("[정산 정보 업데이트] 총 주문 수: {}, 청크 크기: {}", totalSize, CHUNK_SIZE);
        
        List<Map.Entry<String, OrderSettlementData>> entries = new ArrayList<>(mergedSettlementMap.entrySet());
        for (int chunkStart = 0; chunkStart < totalSize; chunkStart += CHUNK_SIZE) {
            int chunkEnd = Math.min(chunkStart + CHUNK_SIZE, totalSize);
            int chunkSize = chunkEnd - chunkStart;
            
            String[] marketplaceOrderIds = new String[chunkSize];
            Long[] commissionAmounts = new Long[chunkSize];
            Long[] shippingCommissionAmounts = new Long[chunkSize];
            Long[] expectedSettlementAmounts = new Long[chunkSize];
            LocalDate[] settlementDates = new LocalDate[chunkSize];

            for (int i = 0; i < chunkSize; i++) {
                Map.Entry<String, OrderSettlementData> entry = entries.get(chunkStart + i);
                marketplaceOrderIds[i] = entry.getKey();
                OrderSettlementData data = entry.getValue();
                
                commissionAmounts[i] = data.productCommission;
                shippingCommissionAmounts[i] = data.shippingCommission;
                expectedSettlementAmounts[i] = data.productSettlement + data.shippingSettlement;
                settlementDates[i] = settlementDate;
                
                if (data.shippingCommission > 0) {
                    log.info("[정산 정보 업데이트] 배송비 수수료 포함 - marketplaceOrderId={}, 상품수수료={}, 배송비수수료={}, 전체정산금액={}", 
                            marketplaceOrderIds[i], data.productCommission, data.shippingCommission, expectedSettlementAmounts[i]);
                }
            }

            int updated = orderRepository.bulkUpdateSettlementInfoByStoreId(
                storeId, marketplaceOrderIds, commissionAmounts, shippingCommissionAmounts,
                expectedSettlementAmounts, settlementDates
            );
            
            totalUpdated += updated;
            log.info("[정산 정보 업데이트] 청크 {}/{} 완료: {} 건 업데이트", 
                    (chunkStart / CHUNK_SIZE + 1), 
                    (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE, 
                    updated);
        }
        
        log.info("[정산 정보 업데이트] 전체 완료: {} 건", totalUpdated);
        return totalUpdated;
    }
    
    /**
     * 주문별 정산 데이터 임시 저장용 클래스
     */
    private static class OrderSettlementData {
        long productCommission = 0L;      // 상품 수수료
        long shippingCommission = 0L;     // 배송비 수수료
        long productSettlement = 0L;      // 상품 정산금액
        long shippingSettlement = 0L;     // 배송비 정산금액
    }

    /**
     * 정산 배치 생성 또는 업데이트 (일별 그룹화)
     */
    private Map<String, SettlementBatch> createOrUpdateBatches(
            UUID tenantId,
            UUID storeId,
            Marketplace marketplace,
            List<DailySettlementElement> elements,
            LocalDate startDate,
            LocalDate endDate) {
        
        // 날짜별 그룹화
        Map<LocalDate, List<DailySettlementElement>> groupedByDate = elements.stream()
                .filter(e -> e.getPayDate() != null)
                .collect(Collectors.groupingBy(e -> LocalDate.parse(e.getPayDate())));

        List<SettlementBatch> batches = new ArrayList<>();
        
        for (Map.Entry<LocalDate, List<DailySettlementElement>> entry : groupedByDate.entrySet()) {
            LocalDate payDate = entry.getKey();
            List<DailySettlementElement> dayElements = entry.getValue();
            
            // 집계 계산
            BigDecimal grossSales = dayElements.stream()
                    .map(e -> BigDecimal.valueOf(e.getPaySettleAmount() != null ? e.getPaySettleAmount() : 0L))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalCommission = dayElements.stream()
                    .map(e -> BigDecimal.valueOf(e.getTotalCommission()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal netPayout = dayElements.stream()
                    .map(e -> BigDecimal.valueOf(e.getCalculatedSettleAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String settlementCycle = payDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            SettlementBatch batch = SettlementBatch.builder()
                    .tenantId(tenantId)
                    .marketplace(marketplace)
                    .settlementCycle(settlementCycle)
                    .settlementPeriodStart(payDate)
                    .settlementPeriodEnd(payDate)
                    .settlementStatus(SettlementStatus.COLLECTED)
                    .totalOrderCount(dayElements.size())
                    .grossSalesAmount(grossSales)
                    .totalCommissionAmount(totalCommission)
                    .totalPgFeeAmount(BigDecimal.ZERO)
                    .totalShippingCharged(BigDecimal.ZERO)
                    .totalShippingSettled(BigDecimal.ZERO)
                    .expectedPayoutAmount(netPayout)
                    .netPayoutAmount(netPayout)
                    .collectedAt(LocalDateTime.now())
                    .build();
            
            batches.add(batch);
        }

        // 벌크 UPSERT (Native Query 방식 - PostgreSQL ON CONFLICT 사용)
        settlementBatchRepository.bulkUpsertNative(batches);

        // 저장된 배치 조회하여 Map 반환
        // ✅ 키를 복합키(날짜 + 마켓플레이스)로 변경하여 충돌 방지
        return batches.stream()
                .map(b -> settlementBatchRepository
                        .findByTenantIdAndMarketplaceAndSettlementCycle(
                            b.getTenantId(), b.getMarketplace(), b.getSettlementCycle())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                    b -> buildBatchKey(b.getSettlementPeriodStart(), b.getMarketplace()),
                    b -> b
                ));
    }

    /**
     * SettlementOrder 벌크 UPSERT (orderId 기준 그룹화)
     * 
     * 변경 사항:
     * - orderId를 기준으로 그룹화하여 하나의 SettlementOrder 생성
     * - 각 productOrderId는 SettlementOrderItem으로 저장
     * - 금액은 상품별 합산
     */
    private int bulkUpsertSettlementOrders(
            UUID tenantId,
            Marketplace marketplace,
            List<DailySettlementElement> elements,
            Map<String, Order> orderMap,
            Map<String, SettlementBatch> batchMap) {
        
        log.info("[정산 주문 적재] ========== 시작 ==========");
        log.info("[정산 주문 적재] API 수집 데이터 총 개수: {}", elements.size());
        
        // 1-1. 필터링 전 데이터 검증
        long nullProductOrderIdCount = elements.stream().filter(e -> e.getProductOrderId() == null).count();
        long nullPayDateCount = elements.stream().filter(e -> e.getPayDate() == null).count();
        long unmatchedOrderCount = elements.stream()
                .filter(e -> e.getProductOrderId() != null && !orderMap.containsKey(e.getProductOrderId()))
                .count();
        
        if (nullProductOrderIdCount > 0) {
            log.warn("[정산 주문 적재] ⚠️ productOrderId가 null인 데이터: {} 건 (저장 불가)", nullProductOrderIdCount);
        }
        if (nullPayDateCount > 0) {
            log.warn("[정산 주문 적재] ⚠️ payDate가 null인 데이터: {} 건 (저장 불가)", nullPayDateCount);
        }
        if (unmatchedOrderCount > 0) {
            log.warn("[정산 주문 적재] ⚠️ 주문 매칭 실패 데이터: {} 건 (orders 테이블에 없음)", unmatchedOrderCount);
            log.warn("[정산 주문 적재]   - 원인: 주문 수집 시점보다 이전에 결제된 주문이거나, 주문 수집이 아직 안된 주문일 수 있음");
            log.warn("[정산 주문 적재]   - 해결: 해당 기간의 주문을 먼저 수집한 후 정산을 다시 실행하세요");
            
            // 매칭 실패한 주문 ID 샘플 출력 (최대 10개, 결제일 포함)
            elements.stream()
                    .filter(e -> e.getProductOrderId() != null && !orderMap.containsKey(e.getProductOrderId()))
                    .limit(10)
                    .forEach(e -> log.warn("[정산 주문 적재]   - 매칭 실패: productOrderId={}, payDate={}, orderId(bundle)={}", 
                            e.getProductOrderId(), e.getPayDate(), e.getOrderId()));
        }
        
        // 1-2. productOrderId + payDate 기준으로 그룹화
        Map<String, List<DailySettlementElement>> groupedByOrder = elements.stream()
                .filter(e -> e.getProductOrderId() != null && e.getPayDate() != null)
                .filter(e -> orderMap.containsKey(e.getProductOrderId()))
                .collect(Collectors.groupingBy(e -> e.getProductOrderId() + "_" + e.getPayDate()));
        
        log.info("[정산 주문 적재] 그룹화 후 주문 수: {} (API 데이터 {} 건 → 필터링 후)", 
                groupedByOrder.size(), elements.size());

        // 기존 SettlementOrderItem의 productOrderId 조회 (중복 방지용)
        Set<UUID> batchIds = batchMap.values().stream()
                .map(SettlementBatch::getSettlementBatchId)
                .collect(Collectors.toSet());
        
        Set<String> existingItemProductOrderIds = settlementOrderItemRepository
                .findProductOrderIdsBySettlementBatchIds(batchIds);
        
        log.info("[정산 주문 적재] DB 기존 Item productOrderId 수: {}", existingItemProductOrderIds.size());

        List<SettlementOrder> settlementOrders = new ArrayList<>();
        int skippedDueToNoBatch = 0;
        int skippedDueToNoOrder = 0;

        for (Map.Entry<String, List<DailySettlementElement>> entry : groupedByOrder.entrySet()) {
            List<DailySettlementElement> bundleElements = entry.getValue();
            if (bundleElements.isEmpty()) {
                continue;
            }

            DailySettlementElement firstElement = bundleElements.get(0);
            String bundleOrderId = firstElement.getOrderId();
            
            // ✅ 디버깅: 번들 내 요소 타입 확인
            long prodOrderCount = bundleElements.stream()
                    .filter(e -> "PROD_ORDER".equals(e.getProductOrderType()))
                    .count();
            long deliveryCountInBundle = bundleElements.stream()
                    .filter(e -> "DELIVERY".equals(e.getProductOrderType()))
                    .count();
            log.info("[정산 주문 적재] 번들 {} 내 요소: PROD_ORDER={}, DELIVERY={}, 총={}", 
                    bundleOrderId, prodOrderCount, deliveryCountInBundle, bundleElements.size());
            
            Order order = orderMap.get(firstElement.getProductOrderId());
            if (order == null) {
                skippedDueToNoOrder++;
                log.debug("[정산 주문 적재] 주문 매핑 실패로 스킵: productOrderId={}", firstElement.getProductOrderId());
                continue;
            }

            LocalDate payDate = LocalDate.parse(firstElement.getPayDate());
            SettlementBatch batch = batchMap.get(buildBatchKey(payDate, marketplace));
            if (batch == null) {
                skippedDueToNoBatch++;
                log.warn("[정산 주문 적재] ⚠️ 배치 없음으로 스킵: payDate={}, productOrderId={}", 
                        payDate, firstElement.getProductOrderId());
                continue;
            }

            // 2. 주문별 금액 집계
            BigDecimal totalGrossSales = BigDecimal.ZERO;
            BigDecimal totalCommission = BigDecimal.ZERO;
            BigDecimal totalNetPayout = BigDecimal.ZERO;

            // 3. SettlementOrder 생성
            // ⚠️ 네이버 정산 API 데이터:
            //    - orderId: bundle_order_id (묶음 배송 주문)
            //    - productOrderId: marketplace_order_id (개별 상품 주문)
            SettlementOrder settlementOrder = SettlementOrder.builder()
                    .tenantId(tenantId)
                    .settlementBatch(batch)
                    .orderId(order.getOrderId())
                    .marketplace(marketplace)
                    .bundleOrderId(firstElement.getOrderId())              // 정산 API orderId = bundle_order_id
                    .marketplaceOrderId(firstElement.getProductOrderId())  // 정산 API productOrderId = marketplace_order_id
                    .grossSalesAmount(BigDecimal.ZERO)  // 임시값, 나중에 집계
                    .commissionAmount(BigDecimal.ZERO)  // 임시값, 나중에 집계
                    .pgFeeAmount(BigDecimal.ZERO)
                    .shippingFeeCharged(BigDecimal.ZERO)
                    .shippingFeeSettled(BigDecimal.ZERO)
                    .netPayoutAmount(BigDecimal.ZERO)  // 임시값, 나중에 집계
                    .build();

            // 4. 각 상품을 SettlementOrderItem으로 추가 (중복 제외)
            // ⚠️ 네이버 정산 API의 productOrderId는 개별 상품 주문 ID (marketplace_order_id)
            Set<String> processedProductOrderIds = new HashSet<>();
            
            for (DailySettlementElement element : bundleElements) {
                String productOrderId = element.getProductOrderId();
                
                // ✅ 동일 SettlementOrder 내에서 productOrderId 중복 방지
                if (processedProductOrderIds.contains(productOrderId)) {
                    log.debug("[정산 주문 적재] 동일 주문 내 중복 productOrderId 스킵: {}", productOrderId);
                    continue;
                }
                
                // ✅ DB에 이미 존재하는 productOrderId 스킵 (같은 배치 내)
                if (existingItemProductOrderIds.contains(productOrderId)) {
                    log.debug("[정산 주문 적재] DB에 이미 존재하는 productOrderId 스킵: {}", productOrderId);
                    continue;
                }
                
                processedProductOrderIds.add(productOrderId);
                
                BigDecimal paySettleAmount = element.getPaySettleAmount() != null 
                        ? BigDecimal.valueOf(element.getPaySettleAmount()) 
                        : BigDecimal.ZERO;
                        
                BigDecimal commissionAmount = BigDecimal.valueOf(element.getTotalCommission());
                
                BigDecimal settleExpectAmount = element.getSettleExpectAmount() != null
                        ? BigDecimal.valueOf(element.getSettleExpectAmount())
                        : BigDecimal.ZERO;

                SettlementOrderItem item = SettlementOrderItem.builder()
                        .marketplaceProductOrderId(productOrderId)  // productOrderId = 개별 상품 주문 ID
                        .productOrderType(element.getProductOrderType())
                        .settleType(element.getSettleType())
                        .productId(element.getProductId())
                        .productName(element.getProductName())
                        .paySettleAmount(paySettleAmount)
                        .totalPayCommissionAmount(element.getTotalPayCommissionAmount() != null
                                ? BigDecimal.valueOf(element.getTotalPayCommissionAmount())
                                : BigDecimal.ZERO)
                        .freeInstallmentCommissionAmount(element.getFreeInstallmentCommissionAmount() != null
                                ? BigDecimal.valueOf(element.getFreeInstallmentCommissionAmount())
                                : BigDecimal.ZERO)
                        .sellingInterlockCommissionAmount(element.getSellingInterlockCommissionAmount() != null
                                ? BigDecimal.valueOf(element.getSellingInterlockCommissionAmount())
                                : BigDecimal.ZERO)
                        .benefitSettleAmount(element.getBenefitSettleAmount() != null
                                ? BigDecimal.valueOf(element.getBenefitSettleAmount())
                                : BigDecimal.ZERO)
                        .settleExpectAmount(settleExpectAmount)
                        .marketplacePayload(convertToJson(element))
                        .build();

                settlementOrder.addItem(item);

                // 금액 집계
                totalGrossSales = totalGrossSales.add(paySettleAmount);
                totalCommission = totalCommission.add(commissionAmount);
                totalNetPayout = totalNetPayout.add(settleExpectAmount);
            }

            // 5. 집계된 금액 설정
            settlementOrder.setGrossSalesAmount(totalGrossSales);
            settlementOrder.setCommissionAmount(totalCommission);
            settlementOrder.setNetPayoutAmount(totalNetPayout);

            settlementOrders.add(settlementOrder);
        }
        
        log.info("[정산 주문 적재] SettlementOrder 생성 완료: {} 건", settlementOrders.size());
        if (skippedDueToNoBatch > 0) {
            log.warn("[정산 주문 적재] ⚠️ 배치 없음으로 스킵: {} 건", skippedDueToNoBatch);
        }
        if (skippedDueToNoOrder > 0) {
            log.warn("[정산 주문 적재] ⚠️ 주문 매핑 실패로 스킵: {} 건", skippedDueToNoOrder);
        }

        if (settlementOrders.isEmpty()) {
            log.warn("[정산 주문 적재] ⚠️ 저장할 데이터가 없습니다.");
            return 0;
        }

        // 6. 기존 SettlementOrder 조회하여 중복 제외
        Set<String> existingKeys = settlementOrderRepository
                .findByTenantIdAndSettlementBatch_SettlementBatchIdIn(tenantId, batchIds)
                .stream()
                .map(so -> buildIdempotencyKey(so.getTenantId(), 
                        so.getSettlementBatch().getSettlementBatchId(),
                        so.getOrderId()))
                .collect(Collectors.toSet());
        
        log.info("[정산 주문 적재] DB 기존 SettlementOrder 수: {}", existingKeys.size());

        // 중복 제외 (DB 기존 데이터 + 현재 배치 내 중복 모두 처리)
        Set<String> processedKeys = new HashSet<>(existingKeys);
        List<SettlementOrder> newOrders = new ArrayList<>();
        int duplicateCount = 0;

        for (SettlementOrder so : settlementOrders) {
            String key = buildIdempotencyKey(so.getTenantId(),
                    so.getSettlementBatch().getSettlementBatchId(),
                    so.getOrderId());
            
            if (!processedKeys.contains(key)) {
                processedKeys.add(key);  // 현재 배치 내 중복 방지
                newOrders.add(so);
            } else {
                duplicateCount++;
                log.debug("[정산 주문 적재] 중복 제외: orderId={}, batchId={}", 
                        so.getOrderId(), so.getSettlementBatch().getSettlementBatchId());
            }
        }
        
        log.info("[정산 주문 적재] 신규 저장 대상: {} 건 (생성 {} 건 중 {} 건 중복 제외)", 
                newOrders.size(), settlementOrders.size(), duplicateCount);

        if (newOrders.isEmpty()) {
            log.warn("[정산 주문 적재] ⚠️ 모두 중복 데이터로 저장할 데이터가 없습니다.");
            log.info("[정산 주문 적재] ========== 완료 (저장 0건) ==========");
            return 0;
        }

        // 7. 벌크 저장 (Cascade로 items도 함께 저장됨)
        List<SettlementOrder> saved = settlementOrderRepository.saveAll(newOrders);
        
        // 저장된 상품 라인 개수 계산
        int totalItems = saved.stream()
                .mapToInt(so -> so.getItems().size())
                .sum();
        
        log.info("[정산 주문 적재] ✅ DB 저장 완료: {} 개 주문, {} 개 상품 라인", saved.size(), totalItems);
        log.info("[정산 주문 적재] ========== 완료 ==========");
        log.info("[정산 주문 적재] 📊 요약:");
        log.info("[정산 주문 적재]   - API 수집: {} 건", elements.size());
        log.info("[정산 주문 적재]   - 필터링 제외: {} 건 (null productOrderId: {}, null payDate: {}, 주문 미매칭: {})", 
                nullProductOrderIdCount + nullPayDateCount + unmatchedOrderCount,
                nullProductOrderIdCount, nullPayDateCount, unmatchedOrderCount);
        log.info("[정산 주문 적재]   - 배치 없음 제외: {} 건", skippedDueToNoBatch);
        log.info("[정산 주문 적재]   - 중복 제외: {} 건", duplicateCount);
        log.info("[정산 주문 적재]   - 최종 저장: {} 건 ({}%)", 
                saved.size(), 
                elements.size() > 0 ? String.format("%.1f", (saved.size() * 100.0 / elements.size())) : "0.0");
        
        return saved.size();
    }

    /**
     * 멱등성 키 생성 (orderId 기준)
     */
    private String buildIdempotencyKey(UUID tenantId, UUID batchId, UUID orderId) {
        return tenantId + "_" + batchId + "_" + orderId;
    }

    /**
     * 객체를 JSON 문자열로 변환
     */
    private String convertToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[정산 수집] JSON 변환 실패", e);
            return "{}";
        }
    }

    /**
     * 배치 맵 키 생성 (날짜 + 마켓플레이스)
     */
    private String buildBatchKey(LocalDate date, Marketplace marketplace) {
        return date.toString() + "_" + marketplace.name();
    }

    /**
     * 마켓 클라이언트 조회
     */
    private MarketplaceSettlementClient getSettlementClient(String marketplace) {
        for (MarketplaceSettlementClient client : marketplaceSettlementClients.values()) {
            if (client.getMarketplaceCode().equalsIgnoreCase(marketplace)) {
                return client;
            }
        }

        throw new IllegalArgumentException("Unsupported marketplace for settlement: " + marketplace);
    }
}
