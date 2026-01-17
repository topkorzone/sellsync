package com.sellsync.api.domain.settlement.service;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.adapter.MarketplaceSettlementClient;
import com.sellsync.api.domain.settlement.dto.CreateSettlementBatchRequest;
import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 정산 수집 서비스
 * 
 * 역할:
 * - 마켓 정산 API 연동
 * - SettlementBatch/SettlementOrder 생성
 * - 금액 집계 및 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCollectionService {

    private final Map<String, MarketplaceSettlementClient> marketplaceSettlementClients;
    private final SettlementService settlementService;
    private final SettlementBatchRepository settlementBatchRepository;

    /**
     * 정산 데이터 수집 및 배치 생성
     * 
     * @param tenantId 테넌트 ID
     * @param marketplace 마켓플레이스
     * @param startDate 시작일
     * @param endDate 종료일
     * @param credentials 인증 정보
     * @return 생성된 정산 배치 목록
     */
    @Transactional
    public List<SettlementBatchResponse> collectSettlements(UUID tenantId,
                                                            Marketplace marketplace,
                                                            LocalDate startDate,
                                                            LocalDate endDate,
                                                            String credentials) {
        log.info("[정산 수집 시작] tenantId={}, marketplace={}, period={} ~ {}", 
            tenantId, marketplace, startDate, endDate);

        // 1. 마켓 클라이언트 선택
        MarketplaceSettlementClient client = getSettlementClient(marketplace.name());

        // 2. 정산 데이터 수집
        List<MarketplaceSettlementData> settlementDataList = client.fetchSettlements(
            startDate, endDate, credentials
        );

        log.info("[정산 데이터 수집 완료] count={}", settlementDataList.size());

        // 3. SettlementBatch 생성
        List<SettlementBatchResponse> batches = settlementDataList.stream()
                .map(data -> createSettlementBatch(tenantId, marketplace, data))
                .toList();

        log.info("[정산 배치 생성 완료] count={}", batches.size());

        return batches;
    }

    /**
     * 정산 배치 생성 (단건)
     */
    @Transactional
    public SettlementBatchResponse createSettlementBatch(UUID tenantId,
                                                         Marketplace marketplace,
                                                         MarketplaceSettlementData data) {
        log.info("[정산 배치 생성] settlementCycle={}", data.getSettlementCycle());

        // 1. SettlementBatch 생성 요청
        CreateSettlementBatchRequest request = CreateSettlementBatchRequest.builder()
                .tenantId(tenantId)
                .marketplace(marketplace)
                .settlementCycle(data.getSettlementCycle())
                .settlementPeriodStart(data.getSettlementPeriodStart())
                .settlementPeriodEnd(data.getSettlementPeriodEnd())
                .marketplaceSettlementId(data.getSettlementId())
                .marketplacePayload(data.getRawPayload())
                .build();

        // 2. SettlementBatch 생성 (멱등성 보장)
        SettlementBatchResponse batch = settlementService.createOrGet(request);

        // 3. SettlementOrder 생성
        if (data.getOrders() != null && !data.getOrders().isEmpty()) {
            createSettlementOrders(batch.getSettlementBatchId(), tenantId, marketplace, data.getOrders());
        }

        // 4. 금액 집계
        SettlementBatch batchEntity = settlementBatchRepository.findById(batch.getSettlementBatchId())
                .orElseThrow();
        
        batchEntity.calculateAggregates();
        settlementBatchRepository.save(batchEntity);

        log.info("[정산 배치 생성 완료] settlementBatchId={}, orderCount={}, netPayout={}", 
            batch.getSettlementBatchId(), 
            data.getOrders().size(),
            batchEntity.getNetPayoutAmount());

        return SettlementBatchResponse.from(batchEntity);
    }

    /**
     * 정산 주문 라인 생성
     */
    private void createSettlementOrders(UUID settlementBatchId,
                                       UUID tenantId,
                                       Marketplace marketplace,
                                       List<MarketplaceSettlementData.SettlementOrderData> orders) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow();

        for (MarketplaceSettlementData.SettlementOrderData orderData : orders) {
            SettlementOrder order = SettlementOrder.builder()
                    .tenantId(tenantId)
                    .orderId(UUID.fromString(orderData.getOrderId()))
                    .settlementType(SettlementType.SALES)
                    .marketplace(marketplace)
                    .marketplaceOrderId(orderData.getMarketplaceOrderId())
                    .grossSalesAmount(orderData.getGrossSalesAmount())
                    .commissionAmount(orderData.getCommissionAmount())
                    .pgFeeAmount(orderData.getPgFeeAmount())
                    .shippingFeeCharged(orderData.getShippingFeeCharged())
                    .shippingFeeSettled(orderData.getShippingFeeSettled())
                    .netPayoutAmount(orderData.getNetPayoutAmount())
                    .build();

            order.calculateNetPayoutAmount();
            batch.addSettlementOrder(order);
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
