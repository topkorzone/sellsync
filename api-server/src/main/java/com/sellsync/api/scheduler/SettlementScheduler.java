package com.sellsync.api.scheduler;

import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.erp.service.ErpConfigService;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.dto.SettlementCollectionResult;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import com.sellsync.api.domain.settlement.service.SettlementCollectionService;
import com.sellsync.api.domain.settlement.service.SettlementPostingService;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 정산 스케줄러
 * 
 * 역할:
 * - 주기적 정산 데이터 수집 (4시간마다)
 * - settlement_batches, settlement_orders 테이블 생성
 * - VALIDATED 상태 중 전표 미생성 배치 자동 전표 생성 (설정에 따라 조건부 실행)
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

    private final SettlementPostingService settlementPostingService;
    private final SettlementBatchRepository settlementBatchRepository;
    private final ErpConfigService erpConfigService;
    private final SettlementCollectionService settlementCollectionService;
    private final StoreRepository storeRepository;
    private final CredentialService credentialService;

    /**
     * 정산 데이터 수집 (4시간마다)
     * 
     * 스케줄: 매일 00, 04, 08, 12, 16, 20시에 실행
     * 
     * 처리 로직:
     * 1. 활성 SmartStore 스토어 목록 조회
     * 2. 각 스토어별로:
     *    a. SettlementCollectionService.collectAndProcessSettlements() 호출
     *    b. settlement_batches, settlement_orders 테이블에 저장
     *    c. 주문 테이블에 수수료 정보 업데이트
     *    d. 정산 배치 상태 COLLECTED로 설정
     * 
     * TODO: 실제 운영에서는 tenant별로 분리 실행 필요
     */
    // @Scheduled(cron = "0 0 */4 * * *") // 4시간마다 (00, 04, 08, 12, 16, 20시)
//     @Scheduled(cron = "0 */5 * * * *") // 5분마다 (테스트용)
    @Scheduled(cron = "0 0 1 * * *")
    public void collectDailySettlements() {
        log.info("[스케줄러] 일별 정산 수집 배치 시작");
        
        try {
            // 1. 활성화된 모든 스토어 목록 조회 (정산 데이터 수집 가능한 마켓플레이스)
            List<Store> activeStores = storeRepository.findByIsActive(true).stream()
                    .filter(store -> store.getMarketplace() == Marketplace.NAVER_SMARTSTORE 
                                  || store.getMarketplace() == Marketplace.COUPANG)
                    .toList();
            
            if (activeStores.isEmpty()) {
                log.info("[스케줄러] 활성화된 스토어가 없습니다.");
                return;
            }
            
            log.info("[스케줄러] 활성 스토어 수: {} (스마트스토어={}, 쿠팡={})", 
                    activeStores.size(),
                    activeStores.stream().filter(s -> s.getMarketplace() == Marketplace.NAVER_SMARTSTORE).count(),
                    activeStores.stream().filter(s -> s.getMarketplace() == Marketplace.COUPANG).count());
            
            // 2. 최근 7일 기간 설정
            LocalDate endDate = LocalDate.now().minusDays(1);  // 어제까지
            LocalDate startDate = endDate.minusDays(6);        // 7일 전부터
            
            int totalBatchCount = 0;
            int totalOrderCount = 0;
            // activeStores.remove(1);
            // 3. 각 스토어별로 정산 데이터 수집 및 처리
            for (Store store : activeStores) {
                try {
                    log.info("========================================");
                    log.info("[스케줄러] 스토어 처리 시작");
                    log.info("[스케줄러]   - 스토어 ID: {}", store.getStoreId());
                    log.info("[스케줄러]   - 스토어명: {}", store.getStoreName());
                    log.info("[스케줄러]   - 마켓플레이스: {}", store.getMarketplace());
                    log.info("[스케줄러]   - 처리 기간: {} ~ {}", startDate, endDate);
                    log.info("========================================");
                    
                    // ✅ credentials 테이블에서 마켓플레이스 인증 정보 조회
                    // 우선순위: credentials 테이블 → stores 테이블 (fallback, 스마트스토어만)
                    Optional<String> credentialsOpt = credentialService.getMarketplaceCredentials(
                            store.getTenantId(), 
                            store.getStoreId(), 
                            store.getMarketplace(),
                            store.getCredentials()  // stores 테이블 credentials 컬럼 (fallback용)
                    );
                    
                    String credentials;
                    if (credentialsOpt.isPresent()) {
                        credentials = credentialsOpt.get();
                        log.info("[스케줄러]   - 인증 정보: 조회 성공 ✅");
                    } else {
                        log.error("[스케줄러] ❌ 인증 정보 없음 - 스킵");
                        log.error("[스케줄러]   - credentials 테이블과 stores 테이블 모두에서 인증 정보를 찾을 수 없습니다.");
                        log.error("[스케줄러]   - 해결: 관리자 화면에서 마켓 연동 정보를 입력해주세요.");
                        continue;
                    }
//                    startDate = endDate.minusMonths(1);
                    // ✅ SettlementCollectionService 호출하여 전체 플로우 처리
                    SettlementCollectionResult result = settlementCollectionService.collectAndProcessSettlements(
                            store.getTenantId(),
                            store.getStoreId(),
                            store.getMarketplace(),
                            startDate,
                            endDate,
                            credentials
                    );
                    
                    totalBatchCount += result.getCreatedBatches();
                    totalOrderCount += result.getCreatedSettlementOrders();
                    
                    log.info("========================================");
                    log.info("[스케줄러] ✅ 스토어 처리 완료");
                    log.info("[스케줄러]   - 스토어 ID: {}", store.getStoreId());
                    log.info("[스케줄러]   - API 수집: {} 건", result.getTotalElements());
                    log.info("[스케줄러]   - 주문 매칭: {} 건", result.getMatchedOrders());
                    log.info("[스케줄러]   - 주문 수수료 업데이트: {} 건", result.getUpdatedOrders());
                    log.info("[스케줄러]   - 정산 배치 생성: {} 건", result.getCreatedBatches());
                    log.info("[스케줄러]   - 정산 주문 저장: {} 건", result.getCreatedSettlementOrders());
                    if (result.getTotalElements() > 0) {
                        double saveRate = (result.getCreatedSettlementOrders() * 100.0) / result.getTotalElements();
                        log.info("[스케줄러]   - 저장 비율: {}/{} ({}%)", 
                                result.getCreatedSettlementOrders(), 
                                result.getTotalElements(),
                                String.format("%.1f", saveRate));
                    }
                    log.info("========================================");
                    
                } catch (Exception e) {
                    log.error("========================================");
                    log.error("[스케줄러] ❌ 스토어 처리 실패");
                    log.error("[스케줄러]   - 스토어 ID: {}", store.getStoreId());
                    log.error("[스케줄러]   - 스토어명: {}", store.getStoreName());
                    log.error("[스케줄러]   - 에러: {}", e.getMessage(), e);
                    log.error("========================================");
                }
            }
            
            log.info("========================================");
            log.info("[스케줄러] ✅ 일별 정산 수집 배치 전체 완료");
            log.info("[스케줄러]   - 처리 스토어 수: {}", activeStores.size());
            log.info("[스케줄러]   - 총 정산 배치: {} 건", totalBatchCount);
            log.info("[스케줄러]   - 총 정산 주문: {} 건", totalOrderCount);
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("[스케줄러] ❌ 일별 정산 수집 배치 실패");
            log.error("[스케줄러]   - 에러: {}", e.getMessage(), e);
            log.error("========================================");
        }
    }

    /**
     * VALIDATED 상태 중 전표 미생성 배치에 대한 자동 전표 생성 (조건부 실행)
     * 
     * 스케줄: 매 10분마다 실행
     * 
     * ⚠️ 중요: 자동 전표 생성은 ERP 설정에서 auto_posting_enabled=true인 경우에만 실행됩니다.
     * 설정 변경: PUT /api/erp/configs/{erpCode} 또는 POST /api/erp/configs/{erpCode}/toggle-auto-posting
     * 
     * 조회 조건:
     * - settlementStatus = VALIDATED
     * - commissionPostingId IS NULL
     * - receiptPostingId IS NULL
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 30000) // 10분마다, 시작 후 30초 대기
    public void processValidatedBatchesWithoutPostings() {
        try {
            log.debug("[스케줄러] 전표 미생성 배치 체크");

            // TODO: 실제 운영에서는 tenant별로 분리 처리
            UUID tenantId = getTenantId(); // Mock
            String erpCode = "ECOUNT";

            // ✅ 자동 전표 생성 설정 확인
            boolean autoPostingEnabled = erpConfigService.isAutoPostingEnabled(tenantId, erpCode);
            
            if (!autoPostingEnabled) {
                log.debug("[스케줄러] 자동 전표 생성 비활성화 - 스킵 (tenant={}, erp={})", tenantId, erpCode);
                return;
            }

            log.info("[스케줄러] 전표 미생성 배치 전표 생성 시작 (자동화 활성화)");

            // VALIDATED 상태이면서 전표가 없는 배치 조회 (최대 5건)
            List<SettlementBatchResponse> batches = settlementBatchRepository
                    .findPendingPostingBatches(tenantId, PageRequest.of(0, 5))
                    .getContent()
                    .stream()
                    .map(SettlementBatchResponse::from)
                    .toList();

            if (batches.isEmpty()) {
                log.debug("[스케줄러] 전표 미생성 배치 없음");
                return;
            }

            log.info("[스케줄러] 전표 미생성 배치 발견: {} 건", batches.size());

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

            log.info("[스케줄러] 전표 생성 완료: {} 건 처리", batches.size());

        } catch (Exception e) {
            log.error("[스케줄러] 전표 생성 실패: {}", e.getMessage(), e);
        }
    }

    // ========== Mock Helper Methods ==========
    // TODO: 실제 구현 시 제거 또는 실제 로직으로 대체

    private UUID getTenantId() {
        // Mock: 실제로는 tenant 목록 조회
        // ⚠️ 실제 DB의 Tenant ID로 수정됨 (2026-01-16)
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }
}
