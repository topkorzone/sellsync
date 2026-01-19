package com.sellsync.api.domain.settlement;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.settlement.dto.CreateSettlementBatchRequest;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.enums.SettlementType;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import com.sellsync.api.domain.settlement.service.SettlementPostingService;
import com.sellsync.api.domain.settlement.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-005 Phase 2] SettlementPostingService 통합 테스트
 * 
 * 목표:
 * - 정산 전표 생성 검증 (수수료, 수금)
 * - TRD v3 금액 계산 로직 검증
 */
@Slf4j
class SettlementPostingServiceTest extends SettlementTestBase {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementPostingService settlementPostingService;

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    @Test
    @DisplayName("[정산 전표 생성] 수수료 + 수금 전표 생성")
    void testCreateSettlementPostings() {
        // Given: 정산 배치 생성 및 금액 설정
        UUID tenantId = UUID.randomUUID();
        SettlementBatch batch = createSettlementBatchWithAmounts(
            tenantId,
            BigDecimal.valueOf(1000000), // 총 매출
            BigDecimal.valueOf(100000),  // 수수료
            BigDecimal.valueOf(20000),   // PG 수수료
            BigDecimal.valueOf(880000)   // 순 입금액
        );

        UUID batchId = batch.getSettlementBatchId();

        log.info("=== [정산 전표 생성 테스트] ===");
        log.info("settlementBatchId={}", batchId);
        log.info("총 매출={}, 수수료={}, PG 수수료={}, 순 입금액={}", 
            batch.getGrossSalesAmount(), 
            batch.getTotalCommissionAmount(),
            batch.getTotalPgFeeAmount(),
            batch.getNetPayoutAmount());

        // Given: VALIDATED 상태로 전환
        settlementService.markAsValidated(batchId);

        // When: 정산 전표 생성
        List<PostingResponse> postings = settlementPostingService.createSettlementPostings(batchId, "ECOUNT");

        // Then: 2개 전표 생성 (수수료 + 수금)
        assertThat(postings).hasSize(2);

        // Then: 수수료 비용 전표 검증
        PostingResponse commissionPosting = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.COMMISSION_EXPENSE)
                .findFirst()
                .orElseThrow();

        assertThat(commissionPosting.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(commissionPosting.getTenantId()).isEqualTo(tenantId);
        assertThat(commissionPosting.getErpCode()).isEqualTo("ECOUNT");
        log.info("✅ 수수료 전표: postingId={}, type={}", 
            commissionPosting.getPostingId(), commissionPosting.getPostingType());

        // Then: 수금 전표 검증
        PostingResponse receiptPosting = postings.stream()
                .filter(p -> p.getPostingType() == PostingType.RECEIPT)
                .findFirst()
                .orElseThrow();

        assertThat(receiptPosting.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(receiptPosting.getTenantId()).isEqualTo(tenantId);
        assertThat(receiptPosting.getErpCode()).isEqualTo("ECOUNT");
        log.info("✅ 수금 전표: postingId={}, type={}", 
            receiptPosting.getPostingId(), receiptPosting.getPostingType());

        // Then: 정산 배치 전표 ID 연결 검증
        SettlementBatchResponse updatedBatch = settlementService.getById(batchId);
        assertThat(updatedBatch.getCommissionPostingId()).isEqualTo(commissionPosting.getPostingId());
        assertThat(updatedBatch.getReceiptPostingId()).isEqualTo(receiptPosting.getPostingId());

        log.info("=== [정산 전표 생성 완료] ===");
    }

    @Test
    @DisplayName("[배송비 차액 전표] 양수 차액 (추가 수익)")
    void testShippingAdjustment_PositiveDiff() {
        // Given: 배송비 차액이 양수인 경우 (정산 배송비 > 고객 결제 배송비)
        UUID tenantId = UUID.randomUUID();
        SettlementOrder order = createSettlementOrderWithShipping(
            tenantId,
            BigDecimal.valueOf(3000),  // 고객 결제 배송비
            BigDecimal.valueOf(3500)   // 마켓 정산 배송비
        );

        // When: 배송비 차액 계산
        BigDecimal adjustment = order.calculateShippingAdjustment();

        // Then: 차액 = 3500 - 3000 = 500 (추가 수익)
        assertThat(adjustment).isEqualByComparingTo(BigDecimal.valueOf(500));
        log.info("✅ 배송비 차액: {} (추가 수익)", adjustment);

        // When: 배송비 차액 전표 생성
        PostingResponse posting = settlementPostingService.createShippingAdjustmentPosting(order, "ECOUNT");

        // Then
        assertThat(posting).isNotNull();
        assertThat(posting.getPostingType()).isEqualTo(PostingType.SHIPPING_ADJUSTMENT);
        assertThat(posting.getPostingStatus()).isEqualTo(PostingStatus.READY);

        log.info("✅ 배송비 차액 전표 생성: postingId={}", posting.getPostingId());
    }

    @Test
    @DisplayName("[배송비 차액 전표] 음수 차액 (추가 비용)")
    void testShippingAdjustment_NegativeDiff() {
        // Given: 배송비 차액이 음수인 경우 (정산 배송비 < 고객 결제 배송비)
        UUID tenantId = UUID.randomUUID();
        SettlementOrder order = createSettlementOrderWithShipping(
            tenantId,
            BigDecimal.valueOf(3000),  // 고객 결제 배송비
            BigDecimal.valueOf(2500)   // 마켓 정산 배송비
        );

        // When: 배송비 차액 계산
        BigDecimal adjustment = order.calculateShippingAdjustment();

        // Then: 차액 = 2500 - 3000 = -500 (추가 비용)
        assertThat(adjustment).isEqualByComparingTo(BigDecimal.valueOf(-500));
        log.info("✅ 배송비 차액: {} (추가 비용)", adjustment);

        // When: 배송비 차액 전표 생성
        PostingResponse posting = settlementPostingService.createShippingAdjustmentPosting(order, "ECOUNT");

        // Then
        assertThat(posting).isNotNull();
        assertThat(posting.getPostingType()).isEqualTo(PostingType.SHIPPING_ADJUSTMENT);

        log.info("✅ 배송비 차액 전표 생성: postingId={}", posting.getPostingId());
    }

    @Test
    @DisplayName("[배송비 차액 전표] 차액 0 → 전표 생성 생략")
    void testShippingAdjustment_ZeroDiff() {
        // Given: 배송비 차액이 0인 경우
        UUID tenantId = UUID.randomUUID();
        SettlementOrder order = createSettlementOrderWithShipping(
            tenantId,
            BigDecimal.valueOf(3000),  // 고객 결제 배송비
            BigDecimal.valueOf(3000)   // 마켓 정산 배송비
        );

        // When: 배송비 차액 전표 생성
        PostingResponse posting = settlementPostingService.createShippingAdjustmentPosting(order, "ECOUNT");

        // Then: 전표 생성하지 않음
        assertThat(posting).isNull();

        log.info("✅ 배송비 차액 0 → 전표 생성 생략");
    }

    // ========== Helper Methods ==========

    private SettlementBatch createSettlementBatchWithAmounts(UUID tenantId,
                                                             BigDecimal grossSales,
                                                             BigDecimal commission,
                                                             BigDecimal pgFee,
                                                             BigDecimal netPayout) {
        SettlementBatch batch = SettlementBatch.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .settlementCycle("2026-W05")
                .settlementPeriodStart(LocalDate.of(2026, 1, 27))
                .settlementPeriodEnd(LocalDate.of(2026, 2, 2))
                .grossSalesAmount(grossSales)
                .totalCommissionAmount(commission)
                .totalPgFeeAmount(pgFee)
                .netPayoutAmount(netPayout)
                .build();

        return settlementBatchRepository.save(batch);
    }

    private SettlementOrder createSettlementOrderWithShipping(UUID tenantId,
                                                              BigDecimal shippingCharged,
                                                              BigDecimal shippingSettled) {
        SettlementOrder order = SettlementOrder.builder()
                .tenantId(tenantId)
                .orderId(UUID.randomUUID())
                .settlementType(SettlementType.SALES)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("ORDER-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .grossSalesAmount(BigDecimal.valueOf(50000))
                .commissionAmount(BigDecimal.valueOf(5000))
                .pgFeeAmount(BigDecimal.valueOf(1000))
                .shippingFeeCharged(shippingCharged)
                .shippingFeeSettled(shippingSettled)
                .netPayoutAmount(BigDecimal.valueOf(44000))
                .build();

        return order;
    }
}
