package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.exception.InvalidStateTransitionException;
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
 * [T-001-1] 상태머신 가드 테스트 (ADR-0001)
 * 
 * 목표:
 * - 허용된 상태 전이는 정상 처리
 * - 금지된 상태 전이는 예외 발생
 */
@Slf4j
@Testcontainers
class PostingStateMachineTest extends PostingTestBase {

    @Autowired
    private PostingService postingService;

    @Test
    @DisplayName("[상태 전이 허용] READY -> READY_TO_POST -> POSTING_REQUESTED -> POSTED")
    void testAllowedTransition_happyPath() {
        // Given: 신규 전표 생성 (READY 상태)
        PostingResponse posting = createNewPosting();
        assertThat(posting.getPostingStatus()).isEqualTo(PostingStatus.READY);
        log.info("신규 전표 생성: postingId={}, status={}", posting.getPostingId(), posting.getPostingStatus());

        // When/Then: READY -> READY_TO_POST
        PostingResponse step1 = postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        assertThat(step1.getPostingStatus()).isEqualTo(PostingStatus.READY_TO_POST);
        log.info("상태 전이 1: READY -> READY_TO_POST");

        // When/Then: READY_TO_POST -> POSTING_REQUESTED
        PostingResponse step2 = postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        assertThat(step2.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);
        log.info("상태 전이 2: READY_TO_POST -> POSTING_REQUESTED");

        // When/Then: POSTING_REQUESTED -> POSTED
        PostingResponse step3 = postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTED);
        assertThat(step3.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(step3.getPostedAt()).isNotNull();
        log.info("상태 전이 3: POSTING_REQUESTED -> POSTED (완료)");
    }

    @Test
    @DisplayName("[상태 전이 허용] POSTING_REQUESTED -> FAILED (실패 케이스)")
    void testAllowedTransition_toFailed() {
        // Given: POSTING_REQUESTED 상태 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);

        // When: POSTING_REQUESTED -> FAILED
        PostingResponse failed = postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED);

        // Then: 실패 상태로 전이
        assertThat(failed.getPostingStatus()).isEqualTo(PostingStatus.FAILED);
        log.info("실패 처리 성공: postingId={}, status={}", failed.getPostingId(), failed.getPostingStatus());
    }

    @Test
    @DisplayName("[상태 전이 허용] FAILED -> POSTING_REQUESTED (재시도)")
    void testAllowedTransition_retryAfterFailure() {
        // Given: FAILED 상태 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED);

        // When: FAILED -> POSTING_REQUESTED (재시도)
        PostingResponse retried = postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);

        // Then: 재시도 상태로 전이
        assertThat(retried.getPostingStatus()).isEqualTo(PostingStatus.POSTING_REQUESTED);
        log.info("재시도 전이 성공: postingId={}, status={}", retried.getPostingId(), retried.getPostingStatus());
    }

    @Test
    @DisplayName("[상태 전이 금지] POSTED -> READY (완료 후 되돌리기 불가)")
    void testForbiddenTransition_postedToReady() {
        // Given: POSTED 상태 전표
        PostingResponse posting = createPostedPosting();

        // When/Then: POSTED -> READY 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.transitionTo(posting.getPostingId(), PostingStatus.READY)
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이")
        .hasMessageContaining("POSTED")
        .hasMessageContaining("READY");

        log.info("[금지 전이 테스트 성공] POSTED -> READY 차단됨");
    }

    @Test
    @DisplayName("[상태 전이 금지] POSTED -> FAILED (완료 후 실패 처리 불가)")
    void testForbiddenTransition_postedToFailed() {
        // Given: POSTED 상태 전표
        PostingResponse posting = createPostedPosting();

        // When/Then: POSTED -> FAILED 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED)
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이")
        .hasMessageContaining("POSTED");

        log.info("[금지 전이 테스트 성공] POSTED -> FAILED 차단됨");
    }

    @Test
    @DisplayName("[상태 전이 금지] FAILED -> READY (실패 후 READY로 직접 불가)")
    void testForbiddenTransition_failedToReady() {
        // Given: FAILED 상태 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);
        postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTING_REQUESTED);
        PostingResponse failed = postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED);

        // When/Then: FAILED -> READY 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.transitionTo(failed.getPostingId(), PostingStatus.READY)
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이")
        .hasMessageContaining("FAILED")
        .hasMessageContaining("READY");

        log.info("[금지 전이 테스트 성공] FAILED -> READY 차단됨 (재시도는 POSTING_REQUESTED로만 가능)");
    }

    @Test
    @DisplayName("[상태 전이 금지] READY -> POSTED (단계 건너뛰기 불가)")
    void testForbiddenTransition_skipSteps() {
        // Given: READY 상태 전표
        PostingResponse posting = createNewPosting();

        // When/Then: READY -> POSTED (중간 단계 건너뛰기) 시도 시 예외 발생
        assertThatThrownBy(() -> 
            postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTED)
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이")
        .hasMessageContaining("READY")
        .hasMessageContaining("POSTED");

        log.info("[금지 전이 테스트 성공] READY -> POSTED 단계 건너뛰기 차단됨");
    }

    @Test
    @DisplayName("[상태 전이 금지] READY_TO_POST -> FAILED (중간 단계에서 실패 불가)")
    void testForbiddenTransition_readyToPostToFailed() {
        // Given: READY_TO_POST 상태 전표
        PostingResponse posting = createNewPosting();
        postingService.transitionTo(posting.getPostingId(), PostingStatus.READY_TO_POST);

        // When/Then: READY_TO_POST -> FAILED 시도 시 예외 발생
        // (실패는 POSTING_REQUESTED 단계에서만 가능)
        assertThatThrownBy(() -> 
            postingService.transitionTo(posting.getPostingId(), PostingStatus.FAILED)
        )
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("금지된 상태 전이");

        log.info("[금지 전이 테스트 성공] READY_TO_POST -> FAILED 차단됨");
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
        return postingService.transitionTo(posting.getPostingId(), PostingStatus.POSTED);
    }
}
