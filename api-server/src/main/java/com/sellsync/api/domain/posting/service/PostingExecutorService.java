package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.posting.adapter.ErpApiClient;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.entity.Posting;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.exception.ErpApiException;
import com.sellsync.api.domain.posting.exception.PostingNotFoundException;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 전표 전송 실행 서비스
 * 
 * 역할:
 * - READY 상태 전표를 ERP로 전송
 * - 상태머신: READY → READY_TO_POST → POSTING_REQUESTED → POSTED
 * - 실패 시: FAILED 상태로 전이 및 재시도 스케줄
 * - ErpApiClient를 통한 ERP 연동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingExecutorService {

    private final PostingService postingService;
    private final PostingRepository postingRepository;
    private final Map<String, ErpApiClient> erpApiClients;
    private final CredentialService credentialService;

    /**
     * 전표 전송 실행
     * 
     * @param postingId 전표 ID
     * @param erpCredentials ERP 인증 정보 (JSON) - null이면 자동 조회
     * @return 전송 결과
     */
    @Transactional
    public PostingResponse executePosting(UUID postingId, String erpCredentials) {
        // 1. Posting 조회
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        log.info("[전표 전송 시작] postingId={}, type={}, status={}, erpCode={}", 
            postingId, posting.getPostingType(), posting.getPostingStatus(), posting.getErpCode());

        // 1-1. credentials 자동 조회 (전달되지 않은 경우)
        if (erpCredentials == null || erpCredentials.trim().isEmpty() || erpCredentials.contains("mock")) {
            final UUID tenantId = posting.getTenantId();
            final String erpCode = posting.getErpCode();
            
            log.info("[Credentials 자동 조회] tenantId={}, erpCode={}", tenantId, erpCode);
            
            erpCredentials = credentialService.getErpCredentials(tenantId, erpCode)
                .orElseThrow(() -> new IllegalStateException(
                    "ERP 인증 정보가 없습니다. tenantId=" + tenantId + ", erpCode=" + erpCode
                ));
            
            log.info("[Credentials 조회 완료] credentialsLength={}", erpCredentials.length());
        }

        try {
            // 2. 상태 전이: READY → READY_TO_POST
            if (posting.getPostingStatus() == PostingStatus.READY) {
                postingService.transitionTo(postingId, PostingStatus.READY_TO_POST);
                log.info("[상태 전이] postingId={}, READY → READY_TO_POST", postingId);
            }

            // 3. 상태 전이: READY_TO_POST → POSTING_REQUESTED
            if (posting.getPostingStatus() == PostingStatus.READY_TO_POST) {
                postingService.transitionTo(postingId, PostingStatus.POSTING_REQUESTED);
                log.info("[상태 전이] postingId={}, READY_TO_POST → POSTING_REQUESTED", postingId);
            }

            // 4. ERP 클라이언트 선택
            ErpApiClient erpClient = getErpApiClient(posting.getErpCode());

            // 5. ERP 전표 전송
            posting = postingRepository.findById(postingId)
                    .orElseThrow(() -> new PostingNotFoundException(postingId));

            String erpDocumentNo = erpClient.postDocument(posting, erpCredentials);
            
            log.info("[ERP 전송 완료] postingId={}, erpDocNo={}", postingId, erpDocumentNo);

            // 6. 전송 성공 처리: POSTING_REQUESTED → POSTED
            PostingResponse result = postingService.markAsPosted(
                postingId, 
                erpDocumentNo, 
                createResponsePayload(erpDocumentNo)
            );

            log.info("[전표 전송 완료] postingId={}, status=POSTED, erpDocNo={}", 
                postingId, erpDocumentNo);

            return result;

        } catch (ErpApiException e) {
            // 7. ERP API 오류 처리
            log.error("[ERP API 오류] postingId={}, errorCode={}, retryable={}", 
                postingId, e.getErrorCode(), e.isRetryable(), e);
            
            PostingResponse failed = postingService.markAsFailed(postingId, e.getMessage());
            
            return failed;

        } catch (Exception e) {
            // 8. 기타 오류
            log.error("[전표 전송 실패] postingId={}, error={}", postingId, e.getMessage(), e);
            
            PostingResponse failed = postingService.markAsFailed(postingId, e.getMessage());
            
            return failed;
        }
    }

    /**
     * 재시도 실행 (FAILED → POSTING_REQUESTED)
     * 
     * @param postingId 전표 ID
     * @param erpCredentials ERP 인증 정보
     * @return 재시도 결과
     */
    @Transactional
    public PostingResponse retry(UUID postingId, String erpCredentials) {
        log.info("[재시도 시작] postingId={}", postingId);

        // 1. FAILED 상태 확인
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        if (!posting.getPostingStatus().isRetryable()) {
            throw new IllegalStateException(
                String.format("재시도 불가능한 상태: postingId=%s, status=%s", 
                    postingId, posting.getPostingStatus())
            );
        }

        // 2. FAILED → POSTING_REQUESTED (재시도 전이)
        postingService.transitionTo(postingId, PostingStatus.POSTING_REQUESTED);
        log.info("[재시도 준비] postingId={}, FAILED → POSTING_REQUESTED", postingId);

        // 3. ERP 클라이언트 선택
        ErpApiClient erpClient = getErpApiClient(posting.getErpCode());

        try {
            // 4. ERP 재전송
            posting = postingRepository.findById(postingId)
                    .orElseThrow(() -> new PostingNotFoundException(postingId));

            String erpDocumentNo = erpClient.postDocument(posting, erpCredentials);
            
            log.info("[재전송 완료] postingId={}, erpDocNo={}", postingId, erpDocumentNo);

            // 5. 전송 성공 처리
            PostingResponse result = postingService.markAsPosted(
                postingId, 
                erpDocumentNo, 
                createResponsePayload(erpDocumentNo)
            );

            log.info("[재시도 성공] postingId={}, erpDocNo={}", postingId, erpDocumentNo);

            return result;

        } catch (ErpApiException e) {
            log.error("[재시도 실패] postingId={}, errorCode={}", postingId, e.getErrorCode(), e);
            
            return postingService.markAsFailed(postingId, e.getMessage());
        }
    }

    /**
     * 배치 전송 (여러 전표 일괄 처리)
     * 
     * @param postingIds 전표 ID 목록
     * @param erpCredentials ERP 인증 정보
     * @return 전송 결과 목록 (성공/실패 포함)
     */
    @Transactional
    public java.util.List<PostingResponse> executePostings(java.util.List<UUID> postingIds, String erpCredentials) {
        java.util.List<PostingResponse> results = new java.util.ArrayList<>();

        for (UUID postingId : postingIds) {
            try {
                PostingResponse result = executePosting(postingId, erpCredentials);
                results.add(result);
            } catch (Exception e) {
                log.error("[배치 전송 실패] postingId={}, error={}", postingId, e.getMessage());
                // 실패해도 다음 전표 계속 처리
            }
        }

        return results;
    }

    /**
     * 재시도 대상 전표 조회
     * 
     * @param tenantId 테넌트 ID
     * @param erpCode ERP 코드
     * @return 재시도 대상 전표 목록
     */
    @Transactional(readOnly = true)
    public java.util.List<PostingResponse> findRetryablePostings(UUID tenantId, String erpCode) {
        return postingRepository.findRetryablePostings(tenantId, erpCode, LocalDateTime.now())
                .stream()
                .map(PostingResponse::from)
                .toList();
    }

    /**
     * READY 상태 전표 조회
     * 
     * @param tenantId 테넌트 ID
     * @param erpCode ERP 코드
     * @return READY 상태 전표 목록
     */
    @Transactional(readOnly = true)
    public java.util.List<PostingResponse> findReadyPostings(UUID tenantId, String erpCode) {
        return postingRepository.findByTenantIdAndErpCodeAndPostingStatusOrderByCreatedAtAsc(
                tenantId, erpCode, PostingStatus.READY, org.springframework.data.domain.PageRequest.of(0, 100)
        )
        .getContent()
        .stream()
        .map(PostingResponse::from)
        .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * ERP API 클라이언트 조회
     */
    private ErpApiClient getErpApiClient(String erpCode) {
        for (ErpApiClient client : erpApiClients.values()) {
            if (client.getErpCode().equalsIgnoreCase(erpCode)) {
                return client;
            }
        }
        
        throw new IllegalArgumentException("Unsupported ERP code: " + erpCode);
    }

    /**
     * 응답 페이로드 생성 (JSON)
     */
    private String createResponsePayload(String erpDocumentNo) {
        return String.format("{\"erpDocNo\":\"%s\",\"postedAt\":\"%s\"}", 
            erpDocumentNo, LocalDateTime.now());
    }
}
