package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.service.PostingService;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [T-001-1] 실패 후 재처리 수렴 테스트 (ADR-0001)
 * 
 * 목표:
 * - 실패한 전표는 재시도 가능
 * - 재처리 후 최종 상태로 수렴
 * - 재시도 불가능한 상태에서는 예외 발생
 */
@Slf4j
@Testcontainers
class PostingReprocessTest extends PostingTestBase {

    @Autowired
    private PostingService postingService;

    @Test
    @DisplayName("[재처리] FAILED 상태 전표 재시도 가능")
    void testReprocess_failedPosting() {
        // Given: 실패한 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        PostingResponse failed = postingService.markAsFailed(posting.getPostingId(), "ERP API 타임아웃");

        assertThat(failed.getPostingStatus()).isEqualTo(PostingStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("ERP API 타임아웃");
        log.info("실패 처리 완료: postingId={}, error={}", failed.getPostingId(), failed.getErrorMessage());

        // When: 재처리 요청
        PostingResponse reprocessed = postingService.reprocess(failed.getPostingId());

        // Then: POSTING_REQUESTED 상태로 전이
        assertThat(reprocessed.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);
        log.info("재처리 성공: postingId={}, status={}", reprocessed.getPostingId(), reprocessed.getPostingStatus());
    }

    @Test
    @DisplayName("[재처리] 실패 -> 재시도 -> 성공 시나리오")
    void testReprocess_failureToSuccess() {
        // Given: 실패한 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        postingService.markAsFailed(posting.getPostingId(), "네트워크 오류");

        log.info("1차 실패: postingId={}", posting.getPostingId());

        // When: 1차 재처리
        PostingResponse retry1 = postingService.reprocess(posting.getPostingId());
        assertThat(retry1.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);
        log.info("1차 재처리: status={}", retry1.getPostingStatus());

        // When: 2차 실패
        PostingResponse failed2 = postingService.markAsFailed(posting.getPostingId(), "ERP 서버 점검 중");
        assertThat(failed2.getPostingStatus()).isEqualTo(PostingStatus.FAILED);
        log.info("2차 실패: error={}", failed2.getErrorMessage());

        // When: 2차 재처리
        PostingResponse retry2 = postingService.reprocess(posting.getPostingId());
        assertThat(retry2.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);
        log.info("2차 재처리: status={}", retry2.getPostingStatus());

        // When: 최종 성공
        PostingResponse success = postingService.markAsPosted(
            posting.getPostingId(), 
            "ERP-DOC-20260112-001", 
            "{\"success\": true}"
        );

        // Then: POSTED 상태로 수렴, 에러 메시지 제거
        assertThat(success.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(success.getErpDocumentNo()).isEqualTo("ERP-DOC-20260112-001");
        assertThat(success.getErrorMessage()).isNull();
        assertThat(success.getPostedAt()).isNotNull();
        
        log.info("최종 성공: postingId={}, erpDocNo={}, postedAt={}", 
            success.getPostingId(), success.getErpDocumentNo(), success.getPostedAt());
    }

    @Test
    @DisplayName("[재처리] 재시도 불가능한 상태(POSTED)에서 재처리 시 예외 발생")
    void testReprocess_notRetryableState() {
        // Given: POSTED 상태 전표
        PostingResponse posting = createPostedPosting();

        // When/Then: 재처리 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.reprocess(posting.getPostingId())
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("재처리 불가능한 상태");

        log.info("[재처리 금지 테스트 성공] POSTED 상태에서는 재처리 불가");
    }

    @Test
    @DisplayName("[재처리] READY 상태에서 재처리 시 예외 발생")
    void testReprocess_readyStateNotRetryable() {
        // Given: READY 상태 전표
        PostingResponse posting = createNewPosting();

        // When/Then: 재처리 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.reprocess(posting.getPostingId())
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("재처리 불가능한 상태");

        log.info("[재처리 금지 테스트 성공] READY 상태에서는 재처리 불가");
    }

    @Test
    @DisplayName("[재처리+멱등성] 동일 전표를 여러 번 재처리해도 안전")
    void testReprocess_idempotentRetry() {
        // Given: 실패한 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        postingService.markAsFailed(posting.getPostingId(), "일시적 오류");

        // When: 여러 번 재처리 (멱등성 검증)
        PostingResponse retry1 = postingService.reprocess(posting.getPostingId());
        assertThat(retry1.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);

        // 다시 FAILED로 전이
        postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED);

        PostingResponse retry2 = postingService.reprocess(posting.getPostingId());
        assertThat(retry2.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);

        // Then: 동일한 postingId, 상태만 변경
        assertThat(retry1.getPostingId()).isEqualTo(retry2.getPostingId());
        assertThat(retry2.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);

        log.info("[재처리 멱등성 테스트 성공] 여러 번 재처리해도 동일 전표: {}", retry2.getPostingId());
    }

    @Test
    @DisplayName("[재처리+시도이력] 재처리 시 PostingAttempt 기록")
    void testReprocess_withAttemptHistory() {
        // Given: 실패한 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        postingService.markAsFailed(posting.getPostingId(), "타임아웃");

        // When: 시도 이력 추가
        postingService.addAttempt(
            posting.getPostingId(),
            1,
            "FAILED",
            "{\"request\": \"payload\"}",
            "{\"error\": \"timeout\"}",
            "TIMEOUT",
            "ERP API 타임아웃"
        );

        // When: 재처리
        PostingResponse retry = postingService.reprocess(posting.getPostingId());

        // When: 2차 시도 이력 추가
        postingService.addAttempt(
            posting.getPostingId(),
            2,
            "SUCCESS",
            "{\"request\": \"payload\"}",
            "{\"success\": true}",
            null,
            null
        );

        // When: 성공 처리
        PostingResponse success = postingService.markAsPosted(
            posting.getPostingId(),
            "ERP-DOC-001",
            "{\"success\": true}"
        );

        // Then: 최종 상태 검증
        assertThat(success.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(success.getErpDocumentNo()).isEqualTo("ERP-DOC-001");

        log.info("[시도 이력 테스트 성공] 재처리 후 성공: postingId={}, attempts=2", 
            success.getPostingId());
    }

    @Test
    @DisplayName("[재처리+수렴] 멱등키 기반 조회 후 재처리")
    void testReprocess_convergenceByIdempotencyKey() {
        // Given: 실패한 전표 (멱등키로 조회 가능)
        UUID tenantId = UUID.randomUUID();
        String erpCode = "ECOUNT";
        String marketplaceOrderId = "ORDER-REPROCESS-001";

        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        PostingResponse posting = postingService.createOrGet(request);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        postingService.markAsFailed(posting.getPostingId(), "실패");

        // When: 멱등키로 조회
        PostingResponse found = postingService.getByIdempotencyKey(
            tenantId, erpCode, Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, PostingType.PRODUCT_SALES
        );

        // Then: 실패한 전표 확인
        assertThat(found).isNotNull();
        assertThat(found.getPostingStatus()).isEqualTo(PostingStatus.FAILED);

        // When: 재처리
        PostingResponse reprocessed = postingService.reprocess(found.getPostingId());
        assertThat(reprocessed.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);

        // When: 성공 처리
        PostingResponse success = postingService.markAsPosted(
            found.getPostingId(),
            "ERP-DOC-002",
            "{\"success\": true}"
        );

        // Then: 멱등키로 재조회 시 성공 상태로 수렴
        PostingResponse final1 = postingService.getByIdempotencyKey(
            tenantId, erpCode, Marketplace.NAVER_SMARTSTORE, marketplaceOrderId, PostingType.PRODUCT_SALES
        );
        assertThat(final1.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(final1.getErpDocumentNo()).isEqualTo("ERP-DOC-002");

        log.info("[멱등키 기반 재처리 테스트 성공] 최종 수렴: postingId={}, status={}", 
            final1.getPostingId(), final1.getPostingStatus());
    }

    // ========== 헬퍼 메서드 ==========

    private PostingResponse createNewPosting() {
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(UUID.randomUUID())
                .erpCode("ECOUNT")
                .orderId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("ORDER-" + UUID.randomUUID())
                .postingType(PostingType.PRODUCT_SALES)
                .build();

        return postingService.createOrGet(request);
    }

    private PostingResponse createPostedPosting() {
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        return postingService.markAsPosted(
            posting.getPostingId(),
            "ERP-DOC-POSTED",
            "{\"success\": true}"
        );
    }
}
