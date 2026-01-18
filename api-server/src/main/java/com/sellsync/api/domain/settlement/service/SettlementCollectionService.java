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
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
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

        // 1. 마켓 API에서 정산 데이터 수집
        MarketplaceSettlementClient client = getSettlementClient(marketplace.name());
        List<DailySettlementElement> elements = client.fetchSettlementElements(startDate, endDate, credentials);
        
        log.info("[정산 데이터 수집 완료] count={}", elements.size());
        
        if (elements.isEmpty()) {
            return SettlementCollectionResult.empty();
        }

        // 2. orderId로 기존 주문 조회 (벌크)
        List<String> marketplaceOrderIds = elements.stream()
                .map(DailySettlementElement::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        
        Map<String, Order> orderMap = orderRepository
                .findByTenantIdAndMarketplaceOrderIdIn(tenantId, marketplaceOrderIds)
                .stream()
                .collect(Collectors.toMap(Order::getMarketplaceOrderId, o -> o));
        
        log.info("[주문 매칭 완료] 요청={}, 매칭={}", marketplaceOrderIds.size(), orderMap.size());

        // 3. 주문 테이블에 수수료 정보 벌크 업데이트
        int updatedOrders = bulkUpdateOrderSettlementInfo(tenantId, elements, orderMap, startDate);
        log.info("[주문 수수료 업데이트 완료] count={}", updatedOrders);

        // 4. SettlementBatch 생성/업데이트 (일별 배치)
        Map<LocalDate, SettlementBatch> batchMap = createOrUpdateBatches(
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
     */
    private int bulkUpdateOrderSettlementInfo(
            UUID tenantId,
            List<DailySettlementElement> elements,
            Map<String, Order> orderMap,
            LocalDate settlementDate) {
        
        // 매칭된 주문만 필터링
        List<DailySettlementElement> matchedElements = elements.stream()
                .filter(e -> e.getOrderId() != null && orderMap.containsKey(e.getOrderId()))
                .toList();
        
        if (matchedElements.isEmpty()) {
            return 0;
        }

        // 배열 준비 (배송비 수수료 추가)
        String[] orderIds = new String[matchedElements.size()];
        Long[] commissionAmounts = new Long[matchedElements.size()];
        Long[] shippingCommissionAmounts = new Long[matchedElements.size()];
        Long[] expectedSettlementAmounts = new Long[matchedElements.size()];
        LocalDate[] settlementDates = new LocalDate[matchedElements.size()];

        for (int i = 0; i < matchedElements.size(); i++) {
            DailySettlementElement e = matchedElements.get(i);
            orderIds[i] = e.getOrderId();
            commissionAmounts[i] = e.getTotalCommission();
            
            // 배송비 수수료 계산: shippingSettleAmount가 있으면 약 1.9% 적용
            // 실제로는 스마트스토어 API에서 배송비 수수료를 명시적으로 제공하지 않으므로 계산
            Long shippingSettleAmount = e.getShippingSettleAmount();
            if (shippingSettleAmount != null && shippingSettleAmount > 0) {
                shippingCommissionAmounts[i] = Math.round(shippingSettleAmount * 0.019);
            } else {
                shippingCommissionAmounts[i] = 0L;
            }
            
            expectedSettlementAmounts[i] = e.getCalculatedSettleAmount();
            settlementDates[i] = settlementDate;
        }

        return orderRepository.bulkUpdateSettlementInfo(
            tenantId, orderIds, commissionAmounts, shippingCommissionAmounts,
            expectedSettlementAmounts, settlementDates
        );
    }

    /**
     * 정산 배치 생성 또는 업데이트 (일별 그룹화)
     */
    private Map<LocalDate, SettlementBatch> createOrUpdateBatches(
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

        // 벌크 UPSERT
        settlementBatchRepository.bulkUpsert(batches);

        // 저장된 배치 조회하여 Map 반환
        return batches.stream()
                .map(b -> settlementBatchRepository
                        .findByTenantIdAndMarketplaceAndSettlementCycle(
                            b.getTenantId(), b.getMarketplace(), b.getSettlementCycle())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                    b -> b.getSettlementPeriodStart(),
                    b -> b
                ));
    }

    /**
     * SettlementOrder 벌크 UPSERT
     */
    private int bulkUpsertSettlementOrders(
            UUID tenantId,
            Marketplace marketplace,
            List<DailySettlementElement> elements,
            Map<String, Order> orderMap,
            Map<LocalDate, SettlementBatch> batchMap) {
        
        List<SettlementOrder> settlementOrders = new ArrayList<>();

        for (DailySettlementElement element : elements) {
            if (element.getOrderId() == null || element.getPayDate() == null) {
                continue;
            }

            Order order = orderMap.get(element.getOrderId());
            if (order == null) {
                continue; // 매칭되는 주문 없음
            }

            LocalDate payDate = LocalDate.parse(element.getPayDate());
            SettlementBatch batch = batchMap.get(payDate);
            if (batch == null) {
                continue;
            }

            // productOrderType에 따라 settlementType 결정
            SettlementType settlementType = determineSettlementType(element.getProductOrderType());
            
            // settlementType에 따라 올바른 수수료 금액 사용
            long commissionAmount;
            if (settlementType == SettlementType.SHIPPING_FEE) {
                // 배송비 수수료: shippingSettleAmount의 약 1.9%
                Long shippingSettleAmount = element.getShippingSettleAmount();
                if (shippingSettleAmount != null && shippingSettleAmount > 0) {
                    commissionAmount = Math.round(shippingSettleAmount * 0.019);
                } else {
                    commissionAmount = 0L;
                }
            } else {
                // 상품 수수료: totalCommission 사용
                commissionAmount = element.getTotalCommission();
            }
            
            SettlementOrder so = SettlementOrder.builder()
                    .tenantId(tenantId)
                    .settlementBatch(batch)
                    .orderId(order.getOrderId())
                    .settlementType(settlementType)
                    .marketplace(marketplace)
                    .marketplaceOrderId(element.getOrderId())
                    .grossSalesAmount(BigDecimal.valueOf(
                        element.getPaySettleAmount() != null ? element.getPaySettleAmount() : 0L))
                    .commissionAmount(BigDecimal.valueOf(commissionAmount))
                    .pgFeeAmount(BigDecimal.ZERO)
                    .shippingFeeCharged(BigDecimal.ZERO)
                    .shippingFeeSettled(BigDecimal.ZERO)
                    .netPayoutAmount(BigDecimal.valueOf(element.getCalculatedSettleAmount()))
                    .marketplacePayload(convertToJson(element))
                    .build();

            settlementOrders.add(so);
        }

        if (settlementOrders.isEmpty()) {
            return 0;
        }

        // ✅ 핵심 수정: 기존 데이터 조회하여 중복 제외
        Set<UUID> batchIds = batchMap.values().stream()
                .map(SettlementBatch::getSettlementBatchId)
                .collect(Collectors.toSet());
        
        // 이미 존재하는 멱등성 키 조회
        Set<String> existingKeys = settlementOrderRepository
                .findByTenantIdAndSettlementBatch_SettlementBatchIdIn(tenantId, batchIds)
                .stream()
                .map(so -> buildIdempotencyKey(so.getTenantId(), 
                        so.getSettlementBatch().getSettlementBatchId(),
                        so.getOrderId(), 
                        so.getSettlementType()))
                .collect(Collectors.toSet());
        
        log.info("[정산 주문] 기존 데이터 수: {}", existingKeys.size());

        // 중복 제외 (DB 기존 데이터 + 현재 배치 내 중복 모두 처리)
        Set<String> processedKeys = new HashSet<>(existingKeys);
        List<SettlementOrder> newOrders = new ArrayList<>();

        for (SettlementOrder so : settlementOrders) {
            String key = buildIdempotencyKey(so.getTenantId(),
                    so.getSettlementBatch().getSettlementBatchId(),
                    so.getOrderId(),
                    so.getSettlementType());
            
            if (!processedKeys.contains(key)) {
                processedKeys.add(key);  // 현재 배치 내 중복 방지
                newOrders.add(so);
            }
        }
        
        log.info("[정산 주문] 신규 저장 대상: {} 건 (전체 {} 건 중 {} 건 중복 제외)", 
                newOrders.size(), settlementOrders.size(), settlementOrders.size() - newOrders.size());

        if (newOrders.isEmpty()) {
            return 0;
        }

        // 벌크 저장 (saveAll 사용, 추후 Native Query로 최적화 가능)
        List<SettlementOrder> saved = settlementOrderRepository.saveAll(newOrders);
        return saved.size();
    }

    /**
     * 멱등성 키 생성
     */
    private String buildIdempotencyKey(UUID tenantId, UUID batchId, UUID orderId, SettlementType type) {
        return tenantId + "_" + batchId + "_" + orderId + "_" + type;
    }

    /**
     * productOrderType에 따라 SettlementType 결정
     * 
     * @param productOrderType 스마트스토어 API의 productOrderType
     * @return SettlementType
     */
    private SettlementType determineSettlementType(String productOrderType) {
        if (productOrderType == null) {
            return SettlementType.SALES; // 기본값
        }
        
        switch (productOrderType) {
            case "DELIVERY":
                return SettlementType.SHIPPING_FEE;
            case "PROD_ORDER":
            default:
                return SettlementType.SALES;
        }
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
