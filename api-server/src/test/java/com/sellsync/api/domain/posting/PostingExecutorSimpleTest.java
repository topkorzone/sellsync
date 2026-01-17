package com.sellsync.api.domain.posting;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.service.PostingExecutor;
import com.sellsync.api.domain.posting.service.PostingExecutorService;
import com.sellsync.api.domain.posting.service.PostingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [T-004] PostingExecutor 간소화 테스트
 * 
 * 핵심 기능만 검증:
 * - READY → POSTED 전송 테스트
 * - 비동기 Worker 검증
 */
@Slf4j
class PostingExecutorSimpleTest extends PostingTestBase {

    @Autowired
    private PostingService postingService;

    @Autowired
    private PostingExecutorService postingExecutorService;

    @Autowired
    private PostingExecutor postingExecutor;

    @Test
    @DisplayName("[핵심] READY → POSTED 전표 전송")
    void testPostingExecution_Simple() {
        // Given: READY 전표 생성
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        log.info("=== [전표 전송 테스트] ===");
        log.info("postingId={}", postingId);

        // When: 전표 전송
        PostingResponse result = postingExecutorService.executePosting(postingId, erpCredentials);

        // Then: POSTED 상태, erpDocumentNo 존재
        assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(result.getErpDocumentNo()).isNotNull();
        assertThat(result.getErpDocumentNo()).startsWith("ECOUNT-");
        assertThat(result.getPostedAt()).isNotNull();

        log.info("✅ 전송 완료: erpDocNo={}, status={}", result.getErpDocumentNo(), result.getPostingStatus());
    }

    @Test
    @DisplayName("[비동기] 비동기 Worker 전송")
    void testAsyncExecution() throws Exception {
        // Given
        UUID postingId = createReadyPosting();
        String erpCredentials = "{\"apiKey\":\"mock-key\"}";

        // When: 비동기 전송
        PostingResponse result = postingExecutor.executeAsync(postingId, erpCredentials)
                .get(30, TimeUnit.SECONDS);

        // Then
        assertThat(result.getPostingStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(result.getErpDocumentNo()).isNotNull();

        log.info("✅ 비동기 전송 완료: erpDocNo={}", result.getErpDocumentNo());
    }

    // ========== Helper ==========

    private UUID createReadyPosting() {
        CreatePostingRequest request = CreatePostingRequest.builder()
                .tenantId(UUID.randomUUID())
                .erpCode("ECOUNT")
                .orderId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("ORDER-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .postingType(PostingType.PRODUCT_SALES)
                .requestPayload("{\"mock\":true}")
                .build();

        PostingResponse posting = postingService.createOrGet(request);
        return posting.getPostingId();
    }
}
