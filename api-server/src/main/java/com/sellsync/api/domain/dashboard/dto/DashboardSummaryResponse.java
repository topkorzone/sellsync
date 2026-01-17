package com.sellsync.api.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 대시보드 요약 응답 DTO
 * 
 * 주요 지표:
 * - 오늘 주문 수
 * - 전표 처리 현황 (성공/실패/대기)
 * - 송장 발급 현황 (성공/실패)
 * - 재시도 대기 건수
 * - 마지막 동기화 시각
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    /**
     * 오늘 주문 수
     */
    private Long todayOrders;

    /**
     * 전표 처리 성공 건수
     */
    private Long postingSuccess;

    /**
     * 전표 처리 실패 건수
     */
    private Long postingFailed;

    /**
     * 전표 처리 대기 건수
     */
    private Long postingPending;

    /**
     * 송장 발급 성공 건수
     */
    private Long shipmentSuccess;

    /**
     * 송장 발급 실패 건수
     */
    private Long shipmentFailed;

    /**
     * 재시도 대기 건수 (전표 + 송장 + 마켓푸시)
     */
    private Long retryQueue;

    /**
     * 마지막 동기화 시각
     */
    private LocalDateTime lastSyncAt;

    /**
     * 오늘 동기화 작업 수
     */
    private Long todaySyncJobs;

    /**
     * 동기화 작업 성공 건수
     */
    private Long syncJobsCompleted;

    /**
     * 동기화 작업 실패 건수
     */
    private Long syncJobsFailed;

    /**
     * 동기화 작업 실행 중 건수
     */
    private Long syncJobsRunning;
}
