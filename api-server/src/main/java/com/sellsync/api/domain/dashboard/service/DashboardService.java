package com.sellsync.api.domain.dashboard.service;

import com.sellsync.api.domain.dashboard.dto.DashboardSummaryResponse;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import com.sellsync.api.domain.shipping.enums.ShipmentLabelStatus;
import com.sellsync.api.domain.shipping.repository.ShipmentLabelRepository;
import com.sellsync.api.domain.shipping.repository.ShipmentMarketPushRepository;
import com.sellsync.api.domain.sync.enums.SyncJobStatus;
import com.sellsync.api.domain.sync.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 대시보드 서비스
 * 
 * 주요 기능:
 * - 오늘의 주문/전표/송장 통계 조회
 * - 재시도 대기 건수 집계
 * - 마지막 동기화 시각 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final com.sellsync.api.domain.order.repository.OrderRepository orderRepository;
    private final PostingRepository postingRepository;
    private final ShipmentLabelRepository shipmentLabelRepository;
    private final ShipmentMarketPushRepository shipmentMarketPushRepository;
    private final SyncJobRepository syncJobRepository;

    /**
     * 대시보드 요약 정보 조회
     * 
     * @param tenantId 테넌트 ID
     * @param date 조회 날짜 (null이면 오늘)
     * @return 대시보드 요약 정보
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UUID tenantId, LocalDate date) {
        // 기준 날짜 (null이면 오늘)
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        log.debug("[대시보드 조회] tenantId={}, date={}, start={}, end={}", 
                tenantId, targetDate, startOfDay, endOfDay);

        // 1. 오늘 주문 수 (생성일 기준)
        long todayOrders = countOrdersCreatedBetween(tenantId, startOfDay, endOfDay);

        // 2. 전표 처리 현황
        long postingSuccess = postingRepository.countByTenantIdAndPostingStatus(
                tenantId, PostingStatus.POSTED
        );
        long postingFailed = postingRepository.countByTenantIdAndPostingStatus(
                tenantId, PostingStatus.FAILED
        );
        long postingPending = postingRepository.countByTenantIdAndPostingStatus(
                tenantId, PostingStatus.READY
        ) + postingRepository.countByTenantIdAndPostingStatus(
                tenantId, PostingStatus.READY_TO_POST
        ) + postingRepository.countByTenantIdAndPostingStatus(
                tenantId, PostingStatus.POSTING_REQUESTED
        );

        // 3. 송장 발급 현황
        long shipmentSuccess = shipmentLabelRepository.countByTenantIdAndLabelStatus(
                tenantId, ShipmentLabelStatus.INVOICE_ISSUED
        );
        long shipmentFailed = shipmentLabelRepository.countByTenantIdAndLabelStatus(
                tenantId, ShipmentLabelStatus.FAILED
        );

        // 4. 재시도 대기 건수
        long retryQueue = countRetryableItems(tenantId);

        // 5. 마지막 동기화 시각
        LocalDateTime lastSyncAt = getLastSyncTime(tenantId);

        // 6. 오늘 동기화 작업 수
        long todaySyncJobs = countSyncJobsCreatedBetween(tenantId, startOfDay, endOfDay);

        // 7. 동기화 작업 상태별 건수
        long syncJobsCompleted = syncJobRepository.countByTenantIdAndSyncStatus(
                tenantId, SyncJobStatus.COMPLETED
        );
        long syncJobsFailed = syncJobRepository.countByTenantIdAndSyncStatus(
                tenantId, SyncJobStatus.FAILED
        );
        long syncJobsRunning = syncJobRepository.countByTenantIdAndSyncStatus(
                tenantId, SyncJobStatus.RUNNING
        );

        DashboardSummaryResponse summary = DashboardSummaryResponse.builder()
                .todayOrders(todayOrders)
                .postingSuccess(postingSuccess)
                .postingFailed(postingFailed)
                .postingPending(postingPending)
                .shipmentSuccess(shipmentSuccess)
                .shipmentFailed(shipmentFailed)
                .retryQueue(retryQueue)
                .lastSyncAt(lastSyncAt)
                .todaySyncJobs(todaySyncJobs)
                .syncJobsCompleted(syncJobsCompleted)
                .syncJobsFailed(syncJobsFailed)
                .syncJobsRunning(syncJobsRunning)
                .build();

        log.info("[대시보드 조회 완료] tenantId={}, date={}, todayOrders={}, postingSuccess={}, retryQueue={}", 
                tenantId, targetDate, todayOrders, postingSuccess, retryQueue);

        return summary;
    }

    /**
     * 기간 내 생성된 주문 수 집계
     */
    private long countOrdersCreatedBetween(UUID tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            return orderRepository.countByTenantIdAndCreatedAtBetween(tenantId, start, end);
        } catch (Exception e) {
            log.warn("[주문 수 집계 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return 0;
        }
    }

    /**
     * 기간 내 생성된 동기화 작업 수 집계
     */
    private long countSyncJobsCreatedBetween(UUID tenantId, LocalDateTime start, LocalDateTime end) {
        try {
            return syncJobRepository.countByTenantIdAndCreatedAtBetween(tenantId, start, end);
        } catch (Exception e) {
            log.warn("[동기화 작업 수 집계 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return 0;
        }
    }

    /**
     * 재시도 대기 건수 집계
     * - 전표 FAILED 건수
     * - 송장 FAILED 건수
     * - 마켓푸시 FAILED 건수 (재시도 가능한 것만)
     */
    private long countRetryableItems(UUID tenantId) {
        try {
            long postingRetryable = postingRepository.countByTenantIdAndPostingStatus(
                    tenantId, PostingStatus.FAILED
            );
            long shipmentRetryable = shipmentLabelRepository.countByTenantIdAndLabelStatus(
                    tenantId, ShipmentLabelStatus.FAILED
            );
            
            // MarketPush 재시도 가능 건수
            long marketPushRetryable = 0;
            try {
                // ShipmentMarketPushRepository에 메서드가 있으면 사용
                marketPushRetryable = shipmentMarketPushRepository
                        .findRetryablePushes(tenantId, LocalDateTime.now())
                        .size();
            } catch (Exception e) {
                log.debug("[마켓푸시 재시도 건수 조회 실패] {}", e.getMessage());
            }

            return postingRetryable + shipmentRetryable + marketPushRetryable;
        } catch (Exception e) {
            log.warn("[재시도 대기 건수 집계 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return 0;
        }
    }

    /**
     * 마지막 동기화 시각 조회
     * - COMPLETED 상태의 가장 최근 작업의 완료 시각
     */
    private LocalDateTime getLastSyncTime(UUID tenantId) {
        try {
            return syncJobRepository
                    .findByTenantIdAndSyncStatusOrderByCreatedAtDesc(
                            tenantId, 
                            SyncJobStatus.COMPLETED, 
                            PageRequest.of(0, 1)
                    )
                    .stream()
                    .findFirst()
                    .map(job -> job.getCompletedAt() != null ? job.getCompletedAt() : job.getUpdatedAt())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[마지막 동기화 시각 조회 실패] tenantId={}, error={}", tenantId, e.getMessage());
            return null;
        }
    }
}
