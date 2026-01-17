package com.sellsync.api.scheduler;

import com.sellsync.api.domain.erp.service.ErpConfigService;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import com.sellsync.api.domain.settlement.service.SettlementCollectionService;
import com.sellsync.api.domain.settlement.service.SettlementPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 정산 스케줄러
 * 
 * 역할:
 * - 주기적 정산 데이터 수집
 * - POSTING_READY 상태 전표 자동 생성 (설정에 따라 조건부 실행)
 * 
 * 주의:
 * - 자동 전표 생성은 ERP 설정에서 활성화한 경우에만 실행됩니다
 * - 실제 운영에서는 tenant별로 분리 실행 필요
 * - 현재는 Mock 구현으로 단일 tenant 가정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementCollectionService settlementCollectionService;
    private final SettlementPostingService settlementPostingService;
    private final SettlementBatchRepository settlementBatchRepository;
    private final ErpConfigService erpConfigService;

    /**
     * 정산 데이터 수집 (주간)
     * 
     * 스케줄: 매주 월요일 오전 2시 실행
     * 
     * TODO: 실제 운영에서는 tenant별로 분리 실행 필요
     */
    @Scheduled(cron = "0 0 2 * * MON") // 매주 월요일 오전 2시
    public void collectWeeklySettlements() {
        try {
            log.info("[스케줄러] 주간 정산 수집 시작");

            // TODO: 실제 운영에서는 tenant 목록 조회 및 반복 처리
            UUID tenantId = getTenantId(); // Mock
            String credentials = getCredentials(tenantId, "NAVER_SMARTSTORE"); // Mock

            // 지난주 정산 데이터 수집
            LocalDate endDate = LocalDate.now().minusDays(7);
            LocalDate startDate = endDate.minusDays(6);

            List<SettlementBatchResponse> batches = settlementCollectionService.collectSettlements(
                tenantId,
                Marketplace.NAVER_SMARTSTORE,
                startDate,
                endDate,
                credentials
            );

            log.info("[스케줄러] 주간 정산 수집 완료: {} 건", batches.size());

        } catch (Exception e) {
            log.error("[스케줄러] 주간 정산 수집 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * POSTING_READY 상태 전표 자동 생성 (조건부 실행)
     * 
     * 스케줄: 매 10분마다 실행
     * 
     * ⚠️ 중요: 자동 전표 생성은 ERP 설정에서 auto_posting_enabled=true인 경우에만 실행됩니다.
     * 설정 변경: PUT /api/erp/configs/{erpCode} 또는 POST /api/erp/configs/{erpCode}/toggle-auto-posting
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 30000) // 10분마다, 시작 후 30초 대기
    public void processPostingReadyBatches() {
        try {
            log.debug("[스케줄러] POSTING_READY 전표 생성 체크");

            // TODO: 실제 운영에서는 tenant별로 분리 처리
            UUID tenantId = getTenantId(); // Mock
            String erpCode = "ECOUNT";

            // ✅ 자동 전표 생성 설정 확인
            boolean autoPostingEnabled = erpConfigService.isAutoPostingEnabled(tenantId, erpCode);
            
            if (!autoPostingEnabled) {
                log.debug("[스케줄러] 자동 전표 생성 비활성화 - 스킵 (tenant={}, erp={})", tenantId, erpCode);
                return;
            }

            log.info("[스케줄러] POSTING_READY 전표 생성 시작 (자동화 활성화)");

            // POSTING_READY 상태 배치 조회 (최대 5건)
            List<SettlementBatchResponse> batches = settlementBatchRepository
                    .findByTenantIdAndSettlementStatusOrderByCollectedAtAsc(
                        tenantId, 
                        SettlementStatus.POSTING_READY, 
                        PageRequest.of(0, 5)
                    )
                    .getContent()
                    .stream()
                    .map(SettlementBatchResponse::from)
                    .toList();

            if (batches.isEmpty()) {
                log.debug("[스케줄러] POSTING_READY 배치 없음");
                return;
            }

            log.info("[스케줄러] POSTING_READY 배치 발견: {} 건", batches.size());

            // 전표 생성
            for (SettlementBatchResponse batch : batches) {
                try {
                    settlementPostingService.createSettlementPostings(
                        batch.getSettlementBatchId(), 
                        erpCode
                    );
                    log.info("[스케줄러] 전표 생성 완료: settlementBatchId={}", batch.getSettlementBatchId());
                } catch (Exception e) {
                    log.error("[스케줄러] 전표 생성 실패: settlementBatchId={}, error={}", 
                        batch.getSettlementBatchId(), e.getMessage());
                }
            }

            log.info("[스케줄러] POSTING_READY 전표 생성 완료: {} 건 처리", batches.size());

        } catch (Exception e) {
            log.error("[스케줄러] POSTING_READY 전표 생성 실패: {}", e.getMessage(), e);
        }
    }

    // ========== Mock Helper Methods ==========
    // TODO: 실제 구현 시 제거 또는 실제 로직으로 대체

    private UUID getTenantId() {
        // Mock: 실제로는 tenant 목록 조회
        // ⚠️ 실제 DB의 Tenant ID로 수정됨 (2026-01-16)
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    private String getCredentials(UUID tenantId, String marketplace) {
        // Mock: 실제로는 tenant별 마켓 인증 정보 조회
        return String.format("{\"tenantId\":\"%s\",\"marketplace\":\"%s\",\"apiKey\":\"mock-key\"}", 
            tenantId, marketplace);
    }
}
