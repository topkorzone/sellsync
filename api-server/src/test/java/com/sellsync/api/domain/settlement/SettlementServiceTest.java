package com.sellsync.api.domain.settlement;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.dto.CreateSettlementBatchRequest;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import com.sellsync.api.domain.settlement.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-005] SettlementService 통합 테스트
 * 
 * 목표:
 * - 정산 배치 생성 및 멱등성 검증
 * - 상태머신 검증
 */
@Slf4j
class SettlementServiceTest extends SettlementTestBase {

    @Autowired
    private SettlementService settlementService;

    @Test
    @DisplayName("[정산 배치 생성] COLLECTED 상태로 생성")
    void testCreateSettlementBatch() {
        // Given
        UUID tenantId = UUID.randomUUID();
        CreateSettlementBatchRequest request = CreateSettlementBatchRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .settlementCycle("2026-W02")
                .settlementPeriodStart(LocalDate.of(2026, 1, 6))
                .settlementPeriodEnd(LocalDate.of(2026, 1, 12))
                .marketplaceSettlementId("NAVER-SETTLE-202602")
                .build();

        // When
        SettlementBatchResponse result = settlementService.createOrGet(request);

        // Then
        assertThat(result.getSettlementBatchId()).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getMarketplace()).isEqualTo(Marketplace.NAVER_SMARTSTORE);
        assertThat(result.getSettlementCycle()).isEqualTo("2026-W02");
        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.COLLECTED);

        log.info("✅ 정산 배치 생성 완료: settlementBatchId={}, status={}", 
            result.getSettlementBatchId(), result.getSettlementStatus());
    }

    @Test
    @DisplayName("[멱등성] 동일 키로 2회 생성 → 동일 배치 반환")
    void testCreateSettlementBatch_Idempotency() {
        // Given
        UUID tenantId = UUID.randomUUID();
        CreateSettlementBatchRequest request = CreateSettlementBatchRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.COUPANG)
                .settlementCycle("2026-W02")
                .settlementPeriodStart(LocalDate.of(2026, 1, 6))
                .settlementPeriodEnd(LocalDate.of(2026, 1, 12))
                .build();

        // When: 1차 생성
        SettlementBatchResponse result1 = settlementService.createOrGet(request);
        UUID batchId1 = result1.getSettlementBatchId();

        // When: 2차 생성 (동일 키)
        SettlementBatchResponse result2 = settlementService.createOrGet(request);
        UUID batchId2 = result2.getSettlementBatchId();

        // Then: 동일한 배치 반환
        assertThat(batchId1).isEqualTo(batchId2);
        assertThat(result1.getSettlementCycle()).isEqualTo(result2.getSettlementCycle());

        log.info("✅ 멱등성 검증 완료: batchId={}", batchId1);
    }

    @Test
    @DisplayName("[상태머신] COLLECTED → VALIDATED → POSTING_READY → POSTED → CLOSED")
    void testSettlementStateMachine() {
        // Given: COLLECTED 상태 배치 생성
        UUID tenantId = UUID.randomUUID();
        CreateSettlementBatchRequest request = CreateSettlementBatchRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .settlementCycle("2026-W03")
                .settlementPeriodStart(LocalDate.of(2026, 1, 13))
                .settlementPeriodEnd(LocalDate.of(2026, 1, 19))
                .build();

        SettlementBatchResponse batch = settlementService.createOrGet(request);
        UUID batchId = batch.getSettlementBatchId();

        assertThat(batch.getSettlementStatus()).isEqualTo(SettlementStatus.COLLECTED);
        log.info("✅ Step 1: COLLECTED");

        // When: COLLECTED → VALIDATED
        SettlementBatchResponse validated = settlementService.markAsValidated(batchId);
        assertThat(validated.getSettlementStatus()).isEqualTo(SettlementStatus.VALIDATED);
        assertThat(validated.getValidatedAt()).isNotNull();
        log.info("✅ Step 2: VALIDATED");

        // When: VALIDATED → POSTING_READY
        SettlementBatchResponse postingReady = settlementService.markAsPostingReady(batchId);
        assertThat(postingReady.getSettlementStatus()).isEqualTo(SettlementStatus.POSTING_READY);
        log.info("✅ Step 3: POSTING_READY");

        // When: POSTING_READY → POSTED
        UUID commissionPostingId = UUID.randomUUID();
        UUID receiptPostingId = UUID.randomUUID();
        SettlementBatchResponse posted = settlementService.markAsPosted(batchId, commissionPostingId, receiptPostingId);
        assertThat(posted.getSettlementStatus()).isEqualTo(SettlementStatus.POSTED);
        assertThat(posted.getCommissionPostingId()).isEqualTo(commissionPostingId);
        assertThat(posted.getReceiptPostingId()).isEqualTo(receiptPostingId);
        assertThat(posted.getPostedAt()).isNotNull();
        log.info("✅ Step 4: POSTED");

        // When: POSTED → CLOSED
        SettlementBatchResponse closed = settlementService.markAsClosed(batchId);
        assertThat(closed.getSettlementStatus()).isEqualTo(SettlementStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
        log.info("✅ Step 5: CLOSED");
    }

    @Test
    @DisplayName("[실패 처리] COLLECTED → FAILED")
    void testMarkAsFailed() {
        // Given
        UUID tenantId = UUID.randomUUID();
        CreateSettlementBatchRequest request = CreateSettlementBatchRequest.builder()
                .tenantId(tenantId)
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .settlementCycle("2026-W04")
                .settlementPeriodStart(LocalDate.of(2026, 1, 20))
                .settlementPeriodEnd(LocalDate.of(2026, 1, 26))
                .build();

        SettlementBatchResponse batch = settlementService.createOrGet(request);
        UUID batchId = batch.getSettlementBatchId();

        // When: 실패 처리
        SettlementBatchResponse failed = settlementService.markAsFailed(
            batchId, 
            "VALIDATION_ERROR", 
            "Invalid settlement data"
        );

        // Then
        assertThat(failed.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(failed.getLastErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(failed.getLastErrorMessage()).contains("Invalid settlement data");
        assertThat(failed.getAttemptCount()).isEqualTo(1);

        log.info("✅ 실패 처리 완료: status=FAILED, errorCode={}, attemptCount={}", 
            failed.getLastErrorCode(), failed.getAttemptCount());
    }
}
