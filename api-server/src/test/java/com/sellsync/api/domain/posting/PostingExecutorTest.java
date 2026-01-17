package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import com.sellsync.api.domain.posting.service.PostingExecutor;
import com.sellsync.api.domain.posting.service.PostingExecutorService;
import com.sellsync.api.domain.posting.service.PostingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-004] PostingExecutor 통합 테스트
 * 
 * 목표:
 * - 전표 ERP 전송 E2E
 * - 상태머신 검증 (READY → POSTED)
 * - 재시도 로직 검증
 * - 비동기 Worker 검증
 */
@Slf4j
@Testcontainers
class PostingExecutorTest extends PostingTestBase {

    @Autowired
    private PostingService postingService;

    @Autowired
    private PostingExecutorService postingExecutorService;

    @Autowired
    private PostingExecutor postingExecutor;

    @Autowired
    private PostingRepository postingRepository;

    @Test
    @DisplayName("[전표 전송] READY → READY_TO_POST → POSTING_REQUESTED → POSTED")
    void testExecutePosting_StateTransitions() {
        // Given: READY 상태 전표 생성
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        log.info("READY 전표 생성: postingId={}", postingId);

        // When: 전표 전송
        PostingResponse result = postingExecutorService.executePosting(postingId, erpCredentials);

        // Then: POSTED 상태
        assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(result.getErpDocumentNo()).isNotNull();
        assertThat(result.getErpDocumentNo()).startsWith("ECOUNT-");
        assertThat(result.getPostedAt()).isNotNull();

        log.info("✅ 전표 전송 완료: postingId={}, erpDocNo={}, status=POSTED", 
            postingId, result.getErpDocumentNo(), result.getPostingStatus());
    }

    @Test
    @DisplayName("[비동기 전송] 비동기 Worker 실행 → POSTED")
    void testExecuteAsync_Success() throws Exception {
        // Given: READY 전표
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        // When: 비동기 전송
        CompletableFuture<PostingResponse> future = postingExecutor.executeAsync(postingId, erpCredentials);

        // Then: 완료 대기
        PostingResponse result = future.get(30, TimeUnit.SECONDS);

        assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(result.getErpDocumentNo()).isNotNull();

        log.info("✅ 비동기 전송 완료: postingId={}, erpDocNo={}", postingId, result.getErpDocumentNo());
    }

    @Test
    @DisplayName("[멱등성] 동일 전표 2회 전송 → 중복 전송 방지 (POSTED 상태 불변)")
    void testExecutePosting_Idempotency() {
        // Given: READY 전표
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        // When: 1차 전송
        PostingResponse result1 = postingExecutorService.executePosting(postingId, erpCredentials);
        assertThat(result1.getPostingStatus()).isEqualTo(PostingStatus.POSTED);

        String erpDocNo1 = result1.getErpDocumentNo();
        log.info("1차 전송: erpDocNo={}", erpDocNo1);

        // When: 2차 전송 시도 (POSTED 상태에서)
        // POSTED → * 전이는 금지되므로 예외 발생 또는 무시됨
        try {
            postingExecutorService.executePosting(postingId, erpCredentials);
            // POSTED 상태는 전이 불가하므로 상태 변경 없음
        } catch (Exception e) {
            log.info("POSTED 상태 전표 재전송 시도 차단: {}", e.getMessage());
        }

        // Then: 여전히 POSTED 상태, 동일한 erpDocNo
        PostingResponse current = postingService.getById(postingId);
        assertThat(current.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(current.getErpDocumentNo()).isEqualTo(erpDocNo1);

        log.info("✅ 멱등성 검증: POSTED 상태 불변, erpDocNo={}", current.getErpDocumentNo());
    }

    @Test
    @DisplayName("[재시도] FAILED → POSTING_REQUESTED → POSTED")
    void testRetry_FailedToPosted() {
        // Given: FAILED 상태 전표 (수동 생성)
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        // 강제로 FAILED 상태로 전환
        postingService.transitionTo(postingId, PostingStatus.READY_TO_POST);
        postingService.transitionTo(postingId, PostingStatus.POSTING_REQUESTED);
        PostingResponse failedPosting = postingService.markAsFailed(postingId, "Test failure");
        
        assertThat(failedPosting.getPostingStatus()).isEqualTo(PostingStatus.FAILED);
        log.info("FAILED 전표 생성: postingId={}", postingId);

        // When: 재시도
        PostingResponse result = postingExecutorService.retry(postingId, erpCredentials);

        // Then: POSTED 상태
        assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(result.getErpDocumentNo()).isNotNull();

        log.info("✅ 재시도 성공: postingId={}, erpDocNo={}", postingId, result.getErpDocumentNo());
    }

    @Test
    @DisplayName("[배치 전송] 3개 전표 동시 전송")
    void testBatchExecution_MultiplePostings() throws Exception {
        // Given: 3개 READY 전표
        List<UUID> postingIds = List.of(
            createReadyPosting(),
            createReadyPosting(),
            createReadyPosting()
        );

        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        log.info("배치 전표 생성: count={}", postingIds.size());

        // When: 배치 전송
        CompletableFuture<Void> batchFuture = postingExecutor.executeBatchAsync(postingIds, erpCredentials);

        // Then: 모두 완료 대기
        batchFuture.get(60, TimeUnit.SECONDS);

        // Then: 모든 전표 POSTED 상태
        for (UUID postingId : postingIds) {
            PostingResponse result = postingService.getById(postingId);
            assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
            assertThat(result.getErpDocumentNo()).isNotNull();
            log.info("전표 완료: postingId={}, erpDocNo={}", postingId, result.getErpDocumentNo());
        }

        log.info("✅ 배치 전송 완료: {} 건 모두 POSTED", postingIds.size());
    }

    @Test
    @DisplayName("[동시성] 동일 전표를 여러 Worker가 동시 전송 시도")
    void testConcurrentExecution_SamePosting() throws Exception {
        // Given: READY 전표
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        // When: 3개 Worker가 동시 전송 시도
        CompletableFuture<PostingResponse> future1 = postingExecutor.executeAsync(postingId, erpCredentials);
        CompletableFuture<PostingResponse> future2 = postingExecutor.executeAsync(postingId, erpCredentials);
        CompletableFuture<PostingResponse> future3 = postingExecutor.executeAsync(postingId, erpCredentials);

        // Then: 모두 완료 (상태머신에 의해 1회만 POSTED 전이)
        try {
            future1.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.info("Worker 1 결과: {}", e.getMessage());
        }

        try {
            future2.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.info("Worker 2 결과: {}", e.getMessage());
        }

        try {
            future3.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.info("Worker 3 결과: {}", e.getMessage());
        }

        // Then: 최종 상태는 POSTED
        PostingResponse finalResult = postingService.getById(postingId);
        assertThat(finalResult.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(finalResult.getErpDocumentNo()).isNotNull();

        log.info("✅ 동시성 테스트 완료: status={}, erpDocNo={}", 
            finalResult.getPostingStatus(), finalResult.getErpDocumentNo());
    }

    @Test
    @DisplayName("[READY 전표 조회] 전송 대상 전표 목록 조회")
    void testFindReadyPostings() {
        // Given: 여러 상태의 전표 생성
        UUID tenantId = UUID.randomUUID();
        String erpCode = "ECOUNT";

        // READY 전표 3개 생성
        createReadyPostingForTenant(tenantId, erpCode);
        createReadyPostingForTenant(tenantId, erpCode);
        createReadyPostingForTenant(tenantId, erpCode);

        // When: READY 전표 조회
        List<PostingResponse> readyPostings = postingExecutorService.findReadyPostings(tenantId, erpCode);

        // Then: 3개 조회됨
        assertThat(readyPostings).hasSizeGreaterThanOrEqualTo(3);
        readyPostings.forEach(p -> assertThat(p.getPostingStatus()).isEqualTo(PostingStatus.READY));

        log.info("✅ READY 전표 조회: {} 건", readyPostings.size());
    }

    @Test
    @DisplayName("[E2E] READY 전표 생성 → 비동기 전송 → POSTED 검증")
    void testFullFlow_ReadyToPosted() throws Exception {
        // Given: READY 전표
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        log.info("=== [E2E 전표 전송 시작] ===");
        log.info("postingId={}", postingId);

        // Step 1: 초기 상태 확인
        PostingResponse initial = postingService.getById(postingId);
        assertThat(initial.getPostingStatus()).isEqualTo(PostingStatus.READY);
        assertThat(initial.getErpDocumentNo()).isNull();
        log.info("✅ Step 1: 초기 상태 READY");

        // Step 2: 비동기 전송
        CompletableFuture<PostingResponse> future = postingExecutor.executeAsync(postingId, erpCredentials);
        PostingResponse result = future.get(30, TimeUnit.SECONDS);
        log.info("✅ Step 2: 비동기 전송 완료");

        // Step 3: 최종 상태 검증
        PostingResponse finalResult = postingService.getById(postingId);
        assertThat(finalResult.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(finalResult.getErpDocumentNo()).isNotNull();
        assertThat(finalResult.getErpDocumentNo()).startsWith("ECOUNT-");
        assertThat(finalResult.getPostedAt()).isNotNull();
        log.info("✅ Step 3: 최종 상태 POSTED, erpDocNo={}", finalResult.getErpDocumentNo());

        log.info("=== [E2E 전표 전송 완료] ===");
    }

    // ========== Helper Methods ==========

    private UUID createReadyPosting() {
        return createReadyPostingForTenant(UUID.randomUUID(), "ECOUNT");
    }

    private UUID createReadyPostingForTenant(UUID tenantId, String erpCode) {
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .orderId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("ORDER-" + UUID.randomUUID().toString().substring(0, 8))
                .postingType(PostingType.PRODUCT_SALES)
                .requestPayload("{\"mock\":true}")
                .build();

        PostingResponse posting = postingService.createOrGet(request);
        return posting.getPostingId();
    }
}
