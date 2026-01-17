package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.posting.dto.PostingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 전표 전송 실행기 (비동기 Worker)
 * 
 * 역할:
 * - READY 상태의 Posting을 백그라운드에서 ERP로 전송
 * - 재시도 대상 전표 자동 재전송
 * - 비동기 처리로 다수의 전표를 동시 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingExecutor {

    private final PostingExecutorService postingExecutorService;

    /**
     * 전표 전송 비동기 실행
     * 
     * @param postingId 전표 ID
     * @param erpCredentials ERP 인증 정보 (JSON)
     * @return 실행 결과 (CompletableFuture)
     */
    @Async("postingTaskExecutor")
    public CompletableFuture<PostingResponse> executeAsync(UUID postingId, String erpCredentials) {
        log.info("[비동기 전송 시작] postingId={}", postingId);

        try {
            PostingResponse result = postingExecutorService.executePosting(postingId, erpCredentials);
            
            log.info("[비동기 전송 완료] postingId={}, status={}, erpDocNo={}", 
                postingId, result.getPostingStatus(), result.getErpDocumentNo());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("[비동기 전송 실패] postingId={}, error={}", postingId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 재시도 대상 전표 비동기 실행
     * 
     * @param postingId 전표 ID
     * @param erpCredentials ERP 인증 정보
     * @return 실행 결과
     */
    @Async("postingTaskExecutor")
    public CompletableFuture<PostingResponse> retryAsync(UUID postingId, String erpCredentials) {
        log.info("[재시도 비동기 실행] postingId={}", postingId);

        try {
            PostingResponse result = postingExecutorService.retry(postingId, erpCredentials);
            
            log.info("[재시도 완료] postingId={}, status={}", postingId, result.getPostingStatus());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("[재시도 실패] postingId={}, error={}", postingId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 배치 전송 (여러 전표 동시 처리)
     * 
     * @param postingIds 전표 ID 목록
     * @param erpCredentials ERP 인증 정보
     * @return 모든 전표 완료 시 Complete
     */
    public CompletableFuture<Void> executeBatchAsync(java.util.List<UUID> postingIds, String erpCredentials) {
        log.info("[배치 전송 시작] count={}", postingIds.size());

        CompletableFuture<?>[] futures = postingIds.stream()
                .map(id -> executeAsync(id, erpCredentials))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> log.info("[배치 전송 완료] count={}", postingIds.size()));
    }

    /**
     * 실패한 전표 재시도 배치 실행
     * 
     * @param postingIds 재시도 대상 전표 ID 목록
     * @param erpCredentials ERP 인증 정보
     * @return 모든 재시도 완료 시 Complete
     */
    public CompletableFuture<Void> retryBatchAsync(java.util.List<UUID> postingIds, String erpCredentials) {
        log.info("[재시도 배치 시작] count={}", postingIds.size());

        CompletableFuture<?>[] futures = postingIds.stream()
                .map(id -> retryAsync(id, erpCredentials))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> log.info("[재시도 배치 완료] count={}", postingIds.size()));
    }
}
