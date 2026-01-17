package com.sellsync.api.domain.settlement.service;

import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.service.PostingService;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.exception.SettlementBatchNotFoundException;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 정산 전표 생성 서비스 (TRD v3)
 * 
 * 역할:
 * - SettlementBatch 기반으로 정산 전표 생성
 * - 수수료 비용 전표 (COMMISSION_EXPENSE)
 * - 배송비 차액 전표 (SHIPPING_ADJUSTMENT)
 * - 수금 전표 (RECEIPT)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPostingService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final PostingService postingService;
    private final SettlementService settlementService;

    /**
     * 정산 배치에 대한 전표 생성 (수수료 + 수금)
     * 
     * TRD v3:
     * 1. COMMISSION_EXPENSE: 총 수수료 (commission + pg_fee)
     * 2. RECEIPT: 순 입금액 (net_payout_amount)
     * 
     * @param settlementBatchId 정산 배치 ID
     * @param erpCode ERP 코드
     * @return 생성된 전표 목록
     */
    @Transactional
    public List<PostingResponse> createSettlementPostings(UUID settlementBatchId, String erpCode) {
        log.info("[정산 전표 생성 시작] settlementBatchId={}, erpCode={}", settlementBatchId, erpCode);

        // 1. 정산 배치 조회
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        List<PostingResponse> postings = new ArrayList<>();

        // 2. 수수료 비용 전표 생성
        PostingResponse commissionPosting = createCommissionExpensePosting(batch, erpCode);
        postings.add(commissionPosting);
        log.info("[수수료 전표 생성] postingId={}, amount={}", 
            commissionPosting.getPostingId(), 
            batch.getTotalCommissionAmount().add(batch.getTotalPgFeeAmount()));

        // 3. 수금 전표 생성
        PostingResponse receiptPosting = createReceiptPosting(batch, erpCode);
        postings.add(receiptPosting);
        log.info("[수금 전표 생성] postingId={}, amount={}", 
            receiptPosting.getPostingId(), batch.getNetPayoutAmount());

        // 4. 배치 상태 업데이트: POSTING_READY → (전표 생성 완료는 PostingExecutor에서 처리)
        // 여기서는 전표 ID만 연결
        settlementService.markAsPosted(
            settlementBatchId, 
            commissionPosting.getPostingId(), 
            receiptPosting.getPostingId()
        );

        log.info("[정산 전표 생성 완료] settlementBatchId={}, 전표 수={}", settlementBatchId, postings.size());

        return postings;
    }

    /**
     * 주문별 배송비 차액 전표 생성
     * 
     * TRD v3: SHIPPING_ADJUSTMENT
     * - 금액: shipping_fee_settled - shipping_fee_charged
     * - 양수: 배송비 추가 수익
     * - 음수: 배송비 추가 비용
     * 
     * @param settlementOrderId 정산 주문 라인 ID
     * @param erpCode ERP 코드
     * @return 배송비 차액 전표
     */
    @Transactional
    public PostingResponse createShippingAdjustmentPosting(SettlementOrder settlementOrder, String erpCode) {
        log.info("[배송비 차액 전표 생성] orderId={}, erpCode={}", settlementOrder.getOrderId(), erpCode);

        BigDecimal shippingAdjustment = settlementOrder.calculateShippingAdjustment();

        // 배송비 차액이 0이면 전표 생성하지 않음
        if (shippingAdjustment.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[배송비 차액 없음] 전표 생성 생략");
            return null;
        }

        // 전표 생성
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(settlementOrder.getTenantId())
                .erpCode(erpCode)
                .orderId(settlementOrder.getOrderId())
                .marketplace(settlementOrder.getMarketplace())
                .marketplaceOrderId(settlementOrder.getMarketplaceOrderId())
                .postingType(PostingType.SHIPPING_ADJUSTMENT)
                .requestPayload(buildShippingAdjustmentPayload(settlementOrder, shippingAdjustment))
                .build();

        PostingResponse posting = postingService.createOrGet(request);

        log.info("[배송비 차액 전표 생성 완료] postingId={}, adjustment={}", 
            posting.getPostingId(), shippingAdjustment);

        return posting;
    }

    // ========== Private Helper Methods ==========

    /**
     * 수수료 비용 전표 생성
     * 
     * TRD v3: COMMISSION_EXPENSE
     * - 금액: total_commission_amount + total_pg_fee_amount
     */
    private PostingResponse createCommissionExpensePosting(SettlementBatch batch, String erpCode) {
        BigDecimal totalFee = batch.getTotalCommissionAmount().add(batch.getTotalPgFeeAmount());

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(batch.getTenantId())
                .erpCode(erpCode)
                .orderId(null) // 정산 배치 전표는 order_id 없음
                .marketplace(batch.getMarketplace())
                .marketplaceOrderId(batch.getMarketplaceSettlementId())
                .postingType(PostingType.COMMISSION_EXPENSE)
                .requestPayload(buildCommissionExpensePayload(batch, totalFee))
                .build();

        return postingService.createOrGet(request);
    }

    /**
     * 수금 전표 생성
     * 
     * TRD v3: RECEIPT
     * - 금액: net_payout_amount
     */
    private PostingResponse createReceiptPosting(SettlementBatch batch, String erpCode) {
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(batch.getTenantId())
                .erpCode(erpCode)
                .orderId(null) // 정산 배치 전표는 order_id 없음
                .marketplace(batch.getMarketplace())
                .marketplaceOrderId(batch.getMarketplaceSettlementId())
                .postingType(PostingType.RECEIPT)
                .requestPayload(buildReceiptPayload(batch))
                .build();

        return postingService.createOrGet(request);
    }

    /**
     * 수수료 비용 전표 Payload 생성 (JSON)
     */
    private String buildCommissionExpensePayload(SettlementBatch batch, BigDecimal totalFee) {
        return String.format(
            "{\"postingType\":\"COMMISSION_EXPENSE\"," +
            "\"settlementCycle\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"totalCommission\":%s," +
            "\"totalPgFee\":%s," +
            "\"totalFee\":%s," +
            "\"orderCount\":%d}",
            batch.getSettlementCycle(),
            batch.getMarketplace(),
            batch.getTotalCommissionAmount(),
            batch.getTotalPgFeeAmount(),
            totalFee,
            batch.getTotalOrderCount()
        );
    }

    /**
     * 수금 전표 Payload 생성 (JSON)
     */
    private String buildReceiptPayload(SettlementBatch batch) {
        return String.format(
            "{\"postingType\":\"RECEIPT\"," +
            "\"settlementCycle\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"grossSales\":%s," +
            "\"totalCommission\":%s," +
            "\"totalPgFee\":%s," +
            "\"netPayout\":%s," +
            "\"orderCount\":%d}",
            batch.getSettlementCycle(),
            batch.getMarketplace(),
            batch.getGrossSalesAmount(),
            batch.getTotalCommissionAmount(),
            batch.getTotalPgFeeAmount(),
            batch.getNetPayoutAmount(),
            batch.getTotalOrderCount()
        );
    }

    /**
     * 배송비 차액 전표 Payload 생성 (JSON)
     */
    private String buildShippingAdjustmentPayload(SettlementOrder order, BigDecimal adjustment) {
        return String.format(
            "{\"postingType\":\"SHIPPING_ADJUSTMENT\"," +
            "\"orderId\":\"%s\"," +
            "\"marketplace\":\"%s\"," +
            "\"marketplaceOrderId\":\"%s\"," +
            "\"shippingCharged\":%s," +
            "\"shippingSettled\":%s," +
            "\"adjustment\":%s}",
            order.getOrderId(),
            order.getMarketplace(),
            order.getMarketplaceOrderId(),
            order.getShippingFeeCharged(),
            order.getShippingFeeSettled(),
            adjustment
        );
    }
}
