package com.sellsync.api.domain.sync.service;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.sync.dto.CreateSyncJobRequest;
import com.sellsync.api.domain.sync.dto.SyncJobResponse;
import com.sellsync.api.domain.sync.entity.SyncJob;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.enums.SyncTriggerType;
import com.sellsync.api.domain.sync.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.sync.exception.SyncJobNotFoundException;
import com.sellsync.api.domain.sync.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 동기화 작업(SyncJob) 서비스 - ADR-0001 멱등성 & 상태머신 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncJobService {

    private final SyncJobRepository syncJobRepository;

    /**
     * 동기화 작업 생성/조회 (멱등 Upsert)
     * - 동일 멱등키로는 1회만 생성
     * - 이미 존재하면 기존 작업 반환
     * 
     * ADR-0001: 멱등키 = tenant_id + store_id + trigger_type + range_hash
     */
    @Transactional
    public SyncJobResponse createOrGet(CreateSyncJobRequest request) {
        String rangeHash = SyncJob.generateRangeHash(
            request.getMarketplace(),
            request.getSyncStartTime(),
            request.getSyncEndTime()
        );

        try {
            // 1. 멱등키로 기존 작업 조회
            return syncJobRepository.findByTenantIdAndStoreIdAndTriggerTypeAndRangeHash(
                    request.getTenantId(),
                    request.getStoreId(),
                    request.getTriggerType(),
                    rangeHash
            )
            .map(existing -> {
                log.info("[멱등성] 기존 동기화 작업 반환: syncJobId={}, status={}, marketplace={}, range={} ~ {}", 
                    existing.getSyncJobId(), existing.getSyncStatus(), existing.getMarketplace(),
                    existing.getSyncStartTime(), existing.getSyncEndTime());
                return SyncJobResponse.from(existing);
            })
            .orElseGet(() -> {
                // 2. 신규 작업 생성
                SyncJob newJob = SyncJob.builder()
                        .tenantId(request.getTenantId())
                        .storeId(request.getStoreId())
                        .triggerType(request.getTriggerType())
                        .rangeHash(rangeHash)
                        .marketplace(request.getMarketplace())
                        .syncStartTime(request.getSyncStartTime())
                        .syncEndTime(request.getSyncEndTime())
                        .syncStatus(SyncJobStatus.PENDING)
                        .requestParams(request.getRequestParams())
                        .traceId(request.getTraceId())
                        .triggeredBy(request.getTriggeredBy())
                        .build();

                SyncJob saved = syncJobRepository.save(newJob);
                log.info("[신규 생성] syncJobId={}, marketplace={}, store={}, range={} ~ {}", 
                    saved.getSyncJobId(), saved.getMarketplace(), saved.getStoreId(),
                    saved.getSyncStartTime(), saved.getSyncEndTime());
                
                return SyncJobResponse.from(saved);
            });
        } catch (DataIntegrityViolationException e) {
            // 3. 동시성: 중복 insert 발생 시 재조회 (멱등 수렴)
            log.warn("[동시성 처리] Unique 제약 위반 감지, 재조회 시도: tenantId={}, storeId={}, trigger={}, hash={}", 
                request.getTenantId(), request.getStoreId(), request.getTriggerType(), rangeHash);
            
            return syncJobRepository.findByTenantIdAndStoreIdAndTriggerTypeAndRangeHash(
                    request.getTenantId(),
                    request.getStoreId(),
                    request.getTriggerType(),
                    rangeHash
            )
            .map(SyncJobResponse::from)
            .orElseThrow(() -> new IllegalStateException("동시성 처리 중 동기화 작업 조회 실패"));
        }
    }

    /**
     * 작업 상태 전이 (ADR-0001 State Machine Guard)
     * - 허용되지 않은 전이는 예외 발생
     */
    @Transactional
    public SyncJobResponse transitionTo(UUID syncJobId, SyncJobStatus targetStatus) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        SyncJobStatus currentStatus = syncJob.getSyncStatus();

        // 상태 전이 가드 검증
        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.error("[상태 전이 금지] syncJobId={}, from={}, to={}", 
                syncJobId, currentStatus, targetStatus);
            throw new InvalidStateTransitionException(syncJobId, currentStatus, targetStatus);
        }

        // 상태 전이 실행
        syncJob.transitionTo(targetStatus);
        SyncJob updated = syncJobRepository.save(syncJob);

        log.info("[상태 전이 성공] syncJobId={}, from={} -> to={}", 
            syncJobId, currentStatus, targetStatus);

        return SyncJobResponse.from(updated);
    }

    /**
     * 작업 시작 (PENDING -> RUNNING)
     */
    @Transactional
    public SyncJobResponse start(UUID syncJobId) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        syncJob.start();
        SyncJob updated = syncJobRepository.save(syncJob);

        log.info("[작업 시작] syncJobId={}, attempt={}, startedAt={}", 
            syncJobId, updated.getAttemptCount(), updated.getStartedAt());

        return SyncJobResponse.from(updated);
    }

    /**
     * 작업 완료 (RUNNING -> COMPLETED)
     */
    @Transactional
    public SyncJobResponse complete(UUID syncJobId, int totalCount, int successCount, int failedCount, String responseSummary) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        syncJob.complete(totalCount, successCount, failedCount);
        syncJob.updateResponseSummary(responseSummary);
        SyncJob updated = syncJobRepository.save(syncJob);

        log.info("[작업 완료] syncJobId={}, total={}, success={}, failed={}, completedAt={}", 
            syncJobId, totalCount, successCount, failedCount, updated.getCompletedAt());

        return SyncJobResponse.from(updated);
    }

    /**
     * 작업 실패 (RUNNING -> FAILED)
     */
    @Transactional
    public SyncJobResponse fail(UUID syncJobId, String errorCode, String errorMessage) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        // 재시도 스케줄 계산: 1m, 5m, 15m, 60m, 180m
        LocalDateTime nextRetry = calculateNextRetry(syncJob.getAttemptCount());
        
        syncJob.fail(errorCode, errorMessage, nextRetry);
        SyncJob updated = syncJobRepository.save(syncJob);

        log.error("[작업 실패] syncJobId={}, attempt={}, error={}, nextRetry={}", 
            syncJobId, updated.getAttemptCount(), errorCode, nextRetry);

        return SyncJobResponse.from(updated);
    }

    /**
     * 작업 진행 상황 업데이트
     */
    @Transactional
    public SyncJobResponse updateProgress(UUID syncJobId, int successCount, int failedCount) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        syncJob.updateProgress(successCount, failedCount);
        SyncJob updated = syncJobRepository.save(syncJob);

        log.debug("[진행 상황 업데이트] syncJobId={}, success={}, failed={}", 
            syncJobId, successCount, failedCount);

        return SyncJobResponse.from(updated);
    }

    /**
     * 작업 재처리 준비 (FAILED -> PENDING)
     */
    @Transactional
    public SyncJobResponse prepareRetry(UUID syncJobId) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));

        if (!syncJob.getSyncStatus().isRetryable()) {
            throw new IllegalStateException(
                String.format("재처리 불가능한 상태: syncJobId=%s, status=%s", 
                    syncJobId, syncJob.getSyncStatus())
            );
        }

        syncJob.prepareRetry();
        SyncJob updated = syncJobRepository.save(syncJob);

        log.info("[재처리 준비] syncJobId={}, status={}", syncJobId, updated.getSyncStatus());

        return SyncJobResponse.from(updated);
    }

    /**
     * 재시도 대상 작업 조회
     */
    @Transactional(readOnly = true)
    public List<SyncJobResponse> findRetryableJobs(UUID tenantId) {
        return syncJobRepository.findRetryableJobs(tenantId, LocalDateTime.now())
                .stream()
                .map(SyncJobResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 타임아웃된 작업 조회 (실행 중 상태가 30분 이상 유지)
     */
    @Transactional(readOnly = true)
    public List<SyncJobResponse> findTimedOutJobs(UUID tenantId, int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return syncJobRepository.findTimedOutJobs(tenantId, threshold)
                .stream()
                .map(SyncJobResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 작업 조회 (ID)
     */
    @Transactional(readOnly = true)
    public SyncJobResponse getById(UUID syncJobId) {
        return syncJobRepository.findById(syncJobId)
                .map(SyncJobResponse::from)
                .orElseThrow(() -> new SyncJobNotFoundException(syncJobId));
    }

    /**
     * 멱등키로 작업 조회
     */
    @Transactional(readOnly = true)
    public SyncJobResponse getByIdempotencyKey(UUID tenantId, UUID storeId, 
                                                 SyncTriggerType triggerType, String rangeHash) {
        return syncJobRepository.findByTenantIdAndStoreIdAndTriggerTypeAndRangeHash(
                tenantId, storeId, triggerType, rangeHash
        )
        .map(SyncJobResponse::from)
        .orElse(null);
    }

    /**
     * 스토어별 작업 목록 조회
     */
    @Transactional(readOnly = true)
    public List<SyncJobResponse> findByStore(UUID tenantId, UUID storeId) {
        return syncJobRepository.findByTenantIdAndStoreIdOrderByCreatedAtDesc(tenantId, storeId)
                .stream()
                .map(SyncJobResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 상태별 작업 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<SyncJobResponse> findByStatus(UUID tenantId, SyncJobStatus status, Pageable pageable) {
        return syncJobRepository.findByTenantIdAndSyncStatusOrderByCreatedAtDesc(tenantId, status, pageable)
                .map(SyncJobResponse::from);
    }

    /**
     * 실패한 작업 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<SyncJobResponse> findFailedJobs(UUID tenantId, Pageable pageable) {
        return syncJobRepository.findFailedJobs(tenantId, pageable)
                .map(SyncJobResponse::from);
    }

    /**
     * 최대 재시도 횟수 초과 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<SyncJobResponse> findMaxRetryExceededJobs(UUID tenantId, Pageable pageable) {
        return syncJobRepository.findMaxRetryExceededJobs(tenantId, pageable)
                .map(SyncJobResponse::from);
    }

    /**
     * 상태별 작업 수 집계
     */
    @Transactional(readOnly = true)
    public long countByStatus(UUID tenantId, SyncJobStatus status) {
        return syncJobRepository.countByTenantIdAndSyncStatus(tenantId, status);
    }

    /**
     * 마켓플레이스 + 상태별 작업 수 집계
     */
    @Transactional(readOnly = true)
    public long countByMarketplaceAndStatus(UUID tenantId, Marketplace marketplace, SyncJobStatus status) {
        return syncJobRepository.countByTenantIdAndMarketplaceAndSyncStatus(tenantId, marketplace, status);
    }

    /**
     * 작업 목록 조회 (페이지네이션, 다양한 필터)
     * 
     * @param tenantId 테넌트 ID (필수)
     * @param storeId 스토어 ID (선택)
     * @param status 작업 상태 (선택)
     * @param pageable 페이지 정보
     * @return 작업 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<SyncJobResponse> getJobs(
            UUID tenantId,
            UUID storeId,
            SyncJobStatus status,
            Pageable pageable
    ) {
        Page<SyncJob> jobs;

        // 1. 스토어 + 상태 필터
        if (storeId != null && status != null) {
            jobs = syncJobRepository.findByTenantIdAndStoreIdAndMarketplaceAndStatus(
                    tenantId, storeId, null, status, pageable
            );
        }
        // 2. 스토어 필터만
        else if (storeId != null) {
            jobs = syncJobRepository.findByTenantIdAndStoreIdOrderByCreatedAtDesc(
                    tenantId, storeId, pageable
            );
        }
        // 3. 상태 필터만
        else if (status != null) {
            jobs = syncJobRepository.findByTenantIdAndSyncStatusOrderByCreatedAtDesc(
                    tenantId, status, pageable
            );
        }
        // 4. 기본 (테넌트만)
        else {
            jobs = syncJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }

        log.debug("[작업 목록 조회] tenantId={}, storeId={}, status={}, total={}", 
                tenantId, storeId, status, jobs.getTotalElements());

        return jobs.map(SyncJobResponse::from);
    }

    // ========== Private Helper Methods ==========

    /**
     * 재시도 스케줄 계산 (백오프)
     * - 1회: 1분 후
     * - 2회: 5분 후
     * - 3회: 15분 후
     * - 4회: 60분 후
     * - 5회 이상: 180분 후
     */
    private LocalDateTime calculateNextRetry(int attemptCount) {
        if (attemptCount >= 5) {
            return null; // 최대 재시도 횟수 초과
        }

        int delayMinutes = switch (attemptCount) {
            case 0 -> 1;    // 첫 실패 후 1분
            case 1 -> 5;    // 두번째 실패 후 5분
            case 2 -> 15;   // 세번째 실패 후 15분
            case 3 -> 60;   // 네번째 실패 후 60분
            default -> 180; // 다섯번째 실패 후 180분
        };

        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
}
