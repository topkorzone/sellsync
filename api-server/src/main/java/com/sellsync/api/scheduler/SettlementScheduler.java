package com.sellsync.api.scheduler;

import com.sellsync.api.domain.credential.service.CredentialService;
import com.sellsync.api.domain.erp.service.ErpConfigService;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.service.OrderSettlementPostingService;
import com.sellsync.api.domain.settlement.dto.SettlementCollectionResult;
import com.sellsync.api.domain.settlement.service.SettlementCollectionService;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import com.sellsync.api.domain.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 정산 스케줄러 (상품별 전표 버전)
 * 
 * 워크플로우:
 * 1. 정산 수집 (새벽 1시)
 *    → 자동 COLLECTED 상태로 마킹
 *    → 자동 상품별 전표 생성 (autoPostingEnabled인 경우)
 *    → 자동 ERP 전송 (autoSendEnabled인 경우)
 * 
 * 2. 모든 활성 테넌트에 대해 순차 처리
 * 3. 스토어별 딜레이로 서버 부하 분산
 * 
 * 주의:
 * - 상품별 전표 생성: OrderSettlementPostingService 사용
 * - 자동 전표 생성/전송은 ERP 설정에서 활성화한 경우에만 실행
 * - 대량 데이터 처리 시 배치 크기 제한으로 timeout 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final OrderSettlementPostingService orderSettlementPostingService;
    private final OrderRepository orderRepository;
    private final ErpConfigService erpConfigService;
    private final SettlementCollectionService settlementCollectionService;
    private final StoreRepository storeRepository;
    private final CredentialService credentialService;
    private final com.sellsync.api.domain.tenant.repository.TenantRepository tenantRepository;
    private final com.sellsync.api.domain.posting.service.PostingExecutor postingExecutor;
    private final ProductMappingService productMappingService;
    
    private static final int MAX_ORDERS_PER_POSTING = 50; // 한 번에 처리할 최대 주문 수
    private static final long DELAY_BETWEEN_TENANTS_MS = 5000; // 테넌트 간 딜레이 (5초)

    /**
     * 정산 데이터 수집 및 자동 처리 (새벽 1시)
     * 
     * 스케줄: 매일 새벽 1시
     * 
     * 통합 워크플로우:
     * 1. 모든 활성 테넌트 조회
     * 2. 각 테넌트별로:
     *    a. 활성 스토어 목록 조회 (SmartStore, Coupang)
     *    b. 정산 데이터 수집 및 자동 COLLECTED 처리
     *    c. ERP 자동 전표 생성 활성화 시 → 상품별 전표 생성 (OrderSettlementPostingService)
     *    d. ERP 자동 전송 활성화 시 → ERP 전송
     * 3. 테넌트 간 딜레이로 서버 부하 분산
     */
     @Scheduled(cron = "0 0 1 * * *") // 매일 새벽 1시
//    @Scheduled(cron = "0 */10 * * * *") // 10분마다 (테스트용)
    @SchedulerLock(name = "collectDailySettlements", lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
    public void collectDailySettlements() {
        log.info("========================================");
        log.info("[스케줄러] 일별 정산 수집 및 자동 처리 시작");
        log.info("========================================");
        
        try {
            // 1. 활성 테넌트 목록 조회
            List<com.sellsync.api.domain.tenant.entity.Tenant> activeTenants = 
                    tenantRepository.findByStatus(com.sellsync.api.domain.tenant.enums.TenantStatus.ACTIVE);
            
            if (activeTenants.isEmpty()) {
                log.info("[스케줄러] 활성 테넌트가 없습니다.");
                return;
            }
            
            log.info("[스케줄러] 활성 테넌트 수: {}", activeTenants.size());
            
            int totalTenantProcessed = 0;
            int totalStoresProcessed = 0;
            int totalBatchCount = 0;
            int totalOrderCount = 0;
            
            // 2. 최근 7일 기간 설정
            LocalDate endDate = LocalDate.now().minusDays(1);  // 어제까지
            LocalDate startDate = endDate.minusMonths(1); // 1달 전부터
//            LocalDate startDate = endDate.minusDays(6);        // 7일 전부터

            // 3. 각 테넌트별로 처리
            for (Tenant tenant : activeTenants) {
                try {
                    log.info("========================================");
                    log.info("[스케줄러] 테넌트 처리 시작");
                    log.info("[스케줄러]   - 테넌트 ID: {}", tenant.getTenantId());
                    log.info("[스케줄러]   - 테넌트명: {}", tenant.getName());
                    log.info("========================================");
                    
                    // 3-1. 테넌트의 활성 스토어 조회
                    List<Store> activeStores = storeRepository
                            .findByTenantIdAndIsActive(tenant.getTenantId(), true)
                            .stream()
                            .filter(store -> store.getMarketplace() == Marketplace.NAVER_SMARTSTORE 
                                          || store.getMarketplace() == Marketplace.COUPANG)
                            .toList();
                    
                    if (activeStores.isEmpty()) {
                        log.info("[스케줄러] 테넌트의 활성 스토어가 없습니다.");
                        continue;
                    }
                    
                    log.info("[스케줄러] 활성 스토어 수: {} (스마트스토어={}, 쿠팡={})", 
                            activeStores.size(),
                            activeStores.stream().filter(s -> s.getMarketplace() == Marketplace.NAVER_SMARTSTORE).count(),
                            activeStores.stream().filter(s -> s.getMarketplace() == Marketplace.COUPANG).count());
                    
                    int tenantBatchCount = 0;
                    int tenantOrderCount = 0;
                    
                    // 3-2. 각 스토어별로 정산 데이터 수집
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
                    // (정산 수집 → 자동 VALIDATED 처리까지 완료됨)
                    SettlementCollectionResult result = settlementCollectionService.collectAndProcessSettlements(
                            store.getTenantId(),
                            store.getStoreId(),
                            store.getMarketplace(),
                            startDate,
                            endDate,
                            credentials
                    );
                    
                    tenantBatchCount += result.getCreatedBatches();
                    tenantOrderCount += result.getCreatedSettlementOrders();
                    
                    log.info("========================================");
                    log.info("[스케줄러] ✅ 스토어 정산 수집 완료");
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
                    
                    totalStoresProcessed += activeStores.size();
                    totalBatchCount += tenantBatchCount;
                    totalOrderCount += tenantOrderCount;
                    
                    // 3-3. 정산 수집 완료 후 자동 전표 생성 및 전송 처리
                    processAutoPostingAndSend(tenant.getTenantId());
                    
                    totalTenantProcessed++;
                    
                    log.info("========================================");
                    log.info("[스케줄러] ✅ 테넌트 처리 완료");
                    log.info("[스케줄러]   - 테넌트 ID: {}", tenant.getTenantId());
                    log.info("[스케줄러]   - 처리 스토어 수: {}", activeStores.size());
                    log.info("[스케줄러]   - 정산 배치: {} 건", tenantBatchCount);
                    log.info("[스케줄러]   - 정산 주문: {} 건", tenantOrderCount);
                    log.info("========================================");
                    
                    // 테넌트 간 딜레이 (서버 부하 분산)
                    if (totalTenantProcessed < activeTenants.size()) {
                        Thread.sleep(DELAY_BETWEEN_TENANTS_MS);
                    }
                    
                } catch (Exception e) {
                    log.error("========================================");
                    log.error("[스케줄러] ❌ 테넌트 처리 실패");
                    log.error("[스케줄러]   - 테넌트 ID: {}", tenant.getTenantId());
                    log.error("[스케줄러]   - 테넌트명: {}", tenant.getName());
                    log.error("[스케줄러]   - 에러: {}", e.getMessage(), e);
                    log.error("========================================");
                }
            }
            
            log.info("========================================");
            log.info("[스케줄러] ✅ 일별 정산 수집 및 자동 처리 전체 완료");
            log.info("[스케줄러]   - 처리 테넌트 수: {}/{}", totalTenantProcessed, activeTenants.size());
            log.info("[스케줄러]   - 처리 스토어 수: {}", totalStoresProcessed);
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
     * 정산 수집 완료 후 자동 전표 생성 및 전송 처리 (상품별 전표)
     * 
     * 워크플로우:
     * 1. COLLECTED 상태 주문 조회 (최대 50건씩 반복)
     * 2. 자동 전표 생성 설정 확인
     * 3. bundleOrderId별로 그룹핑
     * 4. 품목 매핑 완료 여부 사전 체크 ⭐ (매핑 미완료 건 스킵)
     * 5. 매핑 완료된 번들만 상품별 전표 생성
     * 6. COLLECTED 주문이 없을 때까지 반복
     * 7. 자동 전송 설정 확인
     * 8. 활성화 시 → ERP 전송
     */
    private void processAutoPostingAndSend(UUID tenantId) {
        try {
            log.info("========================================");
            log.info("[스케줄러] 자동 전표 생성 및 전송 처리 시작 (상품별 전표)");
            log.info("[스케줄러]   - 테넌트 ID: {}", tenantId);
            log.info("========================================");
            
            String erpCode = "ECOUNT"; // TODO: 테넌트별 ERP 설정에서 가져오기
            
            // 1. 자동 전표 생성 설정 확인
            boolean autoPostingEnabled = erpConfigService.isAutoPostingEnabled(tenantId, erpCode);
            
            log.info("[스케줄러] 자동 전표 생성 설정: {} (erpCode={})", 
                    autoPostingEnabled ? "활성화 ✅" : "비활성화 ❌", erpCode);
            
            if (!autoPostingEnabled) {
                log.info("[스케줄러] 자동 전표 생성 비활성화 - 스킵");
                log.info("[스케줄러]   💡 전표 생성을 원하시면 ERP 설정에서 '자동 전표 생성'을 활성화해주세요.");
                return;
            }
            
            log.info("[스케줄러] 자동 전표 생성 활성화 - 처리 시작");
            
            // 2. 배치 반복 처리 (COLLECTED 주문이 없을 때까지 50건씩)
            List<UUID> allCreatedPostingIds = new java.util.ArrayList<>();
            int totalBatchCount = 0;
            int totalSuccessCount = 0;
            int totalFailCount = 0;
            int totalUnmappedCount = 0;
            int consecutiveEmptyBatches = 0;  // ✅ 연속 빈 배치 카운터
            final int MAX_CONSECUTIVE_EMPTY = 3;  // ✅ 최대 연속 빈 배치 허용 횟수
            int currentPage = 0;  // ✅ 현재 페이지 번호
            
            while (true) {
                totalBatchCount++;
                log.info("========================================");
                log.info("[스케줄러] 배치 {} 시작 (페이지: {})", totalBatchCount, currentPage);
                log.info("========================================");
                
                // COLLECTED 상태 주문 조회 (items JOIN FETCH, 최대 50건)
                // 성능 최적화: tenantId로 DB 레벨 필터링 (Java 필터링 제거)
                log.info("[스케줄러] COLLECTED 상태 주문 조회 중... (페이지: {}, 최대 {} 건)", currentPage, MAX_ORDERS_PER_POSTING);

                List<Order> tenantOrders = orderRepository
                        .findByTenantIdAndSettlementStatusOrderByPaidAtAsc(
                            tenantId,
                            SettlementCollectionStatus.COLLECTED,
                            PageRequest.of(currentPage, MAX_ORDERS_PER_POSTING)
                        );
                
                log.info("[스케줄러] 전표 생성 대상 주문: {} 건", tenantOrders.size());
                
                if (tenantOrders.isEmpty()) {
                    log.info("[스케줄러] ℹ️  더 이상 처리할 COLLECTED 주문 없음");
                    break; // 반복 종료
                }
                
                // 3. bundleOrderId별로 그룹핑 (네이버 스마트스토어: 한 번들에 여러 상품주문)
                // bundleOrderId가 null이면 marketplaceOrderId를 그룹 키로 사용
                java.util.Map<String, List<Order>> ordersByBundle = tenantOrders.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                            o -> o.getBundleOrderId() != null ? o.getBundleOrderId() : o.getMarketplaceOrderId()
                        ));
                
                log.info("[스케줄러] bundleOrderId 그룹 수: {} 개", ordersByBundle.size());
                
                // 4. 품목 매핑 완료 여부 체크 (사전 필터링)
                java.util.Map<String, List<Order>> mappedBundles = new java.util.LinkedHashMap<>();
                java.util.Map<String, String> unmappedBundles = new java.util.LinkedHashMap<>();
                
                for (java.util.Map.Entry<String, List<Order>> entry : ordersByBundle.entrySet()) {
                    String bundleOrderId = entry.getKey();
                    List<Order> orders = entry.getValue();

                    // 번들 내 모든 주문의 모든 상품 매핑 체크
                    java.util.List<String> unmappedItems = checkBundleProductMappings(orders);
                    
                    if (unmappedItems.isEmpty()) {
                        // 모든 상품이 매핑됨 → 전표 생성 대상
                        mappedBundles.put(bundleOrderId, orders);
                        log.info("[스케줄러] ✅ 전표 생성 대상 추가: bundleOrderId={}, 주문 개수={}", 
                                bundleOrderId, orders.size());
                    } else {
                        // 일부 상품 매핑 안 됨 → 스킵
                        String unmappedItemsStr = String.join(", ", unmappedItems);
                        unmappedBundles.put(bundleOrderId, unmappedItemsStr);
                        log.warn("[스케줄러] ⚠️ 전표 생성 제외 (품목 매핑 미완료): bundleOrderId={}, 미매핑 개수={}", 
                                bundleOrderId, unmappedItems.size());
                    }
                }
                
                log.info("========================================");
                log.info("[스케줄러] 품목 매핑 체크 완료");
                log.info("[스케줄러]   - 전체 번들: {} 개", ordersByBundle.size());
                log.info("[스케줄러]   - 매핑 완료 (전표 생성 대상): {} 개 ✅", mappedBundles.size());
                log.info("[스케줄러]   - 매핑 미완료 (전표 생성 제외): {} 개 ⚠️", unmappedBundles.size());
                log.info("========================================");
                
                totalUnmappedCount += unmappedBundles.size();
                
                if (mappedBundles.isEmpty()) {
                    consecutiveEmptyBatches++;  // ✅ 빈 배치 카운트 증가
                    currentPage++;  // ✅ 다음 페이지로 이동 (현재 페이지는 모두 미매핑)
                    
                    log.warn("========================================");
                    log.warn("[스케줄러] ⚠️  이번 배치에 품목 매핑이 완료된 주문 없음 ({}/{})", 
                            consecutiveEmptyBatches, MAX_CONSECUTIVE_EMPTY);
                    log.warn("[스케줄러]   - 현재 배치의 모든 주문이 품목 매핑 미완료 상태입니다.");
                    log.warn("[스케줄러]   - 다음 페이지({})로 이동하여 계속 조회합니다.", currentPage);
                    
                    if (consecutiveEmptyBatches >= MAX_CONSECUTIVE_EMPTY) {
                        log.warn("[스케줄러]   🛑 {} 회 연속 빈 배치 → 처리 중단", MAX_CONSECUTIVE_EMPTY);
                        log.warn("[스케줄러]   💡 매핑 관리 화면에서 상품 매핑을 완료한 후 다시 시도해주세요.");
                        if (!unmappedBundles.isEmpty()) {
                            log.warn("[스케줄러]   최근 미매핑 번들 목록:");
                            unmappedBundles.entrySet().stream().limit(5).forEach(entry -> 
                                log.warn("[스케줄러]     - bundleOrderId={}, 미매핑 상품={}", 
                                        entry.getKey(), entry.getValue())
                            );
                        }
                        log.warn("========================================");
                        break; // 연속 실패 → 종료
                    }
                    
                    log.warn("[스케줄러]   → 다음 배치 확인 계속...");
                    log.warn("========================================");
                    continue; // ✅ 현재 배치 스킵하고 다음 배치 조회
                }
                
                // ✅ 매핑된 주문이 있으면 카운터 리셋 (페이지는 리셋하지 않음 - 처리된 주문은 POSTED로 변경됨)
                consecutiveEmptyBatches = 0;
                
                log.info("[스케줄러] 상품별 전표 생성 시작 - 대상: {} 개 번들", mappedBundles.size());
                
                // 5. 전표 생성 (매핑 완료된 bundleOrderId만)
                int batchSuccessCount = 0;
                int batchFailCount = 0;
                
                for (String bundleOrderId : mappedBundles.keySet()) {
                    try {
                        log.info("[스케줄러] 상품별 전표 생성 시도: bundleOrderId={}", bundleOrderId);
                        
                        com.sellsync.api.domain.posting.dto.PostingResponse posting = 
                                orderSettlementPostingService.createPostingsForSettledOrder(
                                    bundleOrderId, 
                                    erpCode
                                );
                        
                        allCreatedPostingIds.add(posting.getPostingId());
                        batchSuccessCount++;
                        
                        log.info("[스케줄러] ✅ 상품별 전표 생성 완료: bundleOrderId={}, postingId={}", 
                                bundleOrderId, posting.getPostingId());
                    } catch (Exception e) {
                        batchFailCount++;
                        log.error("[스케줄러] ❌ 상품별 전표 생성 실패: bundleOrderId={}, error={}", 
                                bundleOrderId, e.getMessage(), e);
                    }
                }
                
                totalSuccessCount += batchSuccessCount;
                totalFailCount += batchFailCount;
                
                log.info("[스케줄러] 배치 {} 완료 - 성공: {}, 실패: {}", 
                        totalBatchCount, batchSuccessCount, batchFailCount);
                
                // 6. 배치 간 짧은 딜레이 (DB 부하 분산)
                if (tenantOrders.size() == MAX_ORDERS_PER_POSTING) {
                    log.info("[스케줄러] 다음 배치 처리 전 대기... (2초)");
                    Thread.sleep(2000);
                } else {
                    // 50건 미만이면 마지막 배치 → 종료
                    break;
                }
            }
            
            log.info("========================================");
            log.info("[스케줄러] 전체 배치 처리 완료");
            log.info("[스케줄러]   - 총 배치 수: {}", totalBatchCount);
            log.info("[스케줄러]   - 전표 생성 성공: {} 번들", totalSuccessCount);
            log.info("[스케줄러]   - 전표 생성 실패: {} 번들", totalFailCount);
            log.info("[스케줄러]   - 매핑 미완료 스킵: {} 번들", totalUnmappedCount);
            log.info("[스케줄러]   - 생성된 전표: {} 개", allCreatedPostingIds.size());
            log.info("========================================");
            
            // 7. 자동 전송 설정 확인 및 전송
            if (allCreatedPostingIds.isEmpty()) {
                log.info("[스케줄러] ℹ️  생성된 전표 없음 - 전송 스킵");
                return;
            }
            
            boolean autoSendEnabled = erpConfigService.isAutoSendEnabled(tenantId, erpCode);
            
            log.info("[스케줄러] 자동 ERP 전송 설정: {} (erpCode={})", 
                    autoSendEnabled ? "활성화 ✅" : "비활성화 ❌", erpCode);
            
            if (!autoSendEnabled) {
                log.info("[스케줄러] 자동 전송 비활성화 - 전송 스킵");
                log.info("[스케줄러]   💡 ERP 전송을 원하시면 ERP 설정에서 '자동 전송'을 활성화해주세요.");
                return;
            }
            
            log.info("========================================");
            log.info("[스케줄러] ERP 전송 시작");
            log.info("[스케줄러]   - 전송 대상: {} 개 전표", allCreatedPostingIds.size());
            log.info("========================================");
            
            String erpCredentials = getErpCredentials(tenantId, erpCode);
            postingExecutor.executeBatchAsync(allCreatedPostingIds, erpCredentials);
            
            log.info("[스케줄러] ✅ ERP 전송 시작 완료 (비동기 처리 중)");
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("[스케줄러] ❌ 자동 전표 생성 및 전송 실패");
            log.error("[스케줄러]   - 에러: {}", e.getMessage(), e);
            log.error("========================================");
        }
    }

    /**
     * 배치 단위 전표 생성 메서드 (Deprecated)
     * 
     * ⚠️ 주의: 이 메서드는 더 이상 사용되지 않습니다.
     * 배치 단위 전표 대신 상품별 전표 생성으로 변경되었습니다.
     * 
     * @deprecated 상품별 전표 생성으로 대체됨 (processAutoPostingAndSend → OrderSettlementPostingService)
     */
    // @Scheduled(fixedDelay = 600000, initialDelay = 30000) // 10분마다, 시작 후 30초 대기
    @Deprecated
    public void processValidatedBatchesWithoutPostings() {
        try {
            log.debug("[스케줄러] 전표 미생성 배치 체크 (비활성화됨)");

            // ⚠️ 이 메서드는 더 이상 사용되지 않습니다.
            // 정산 수집 후 자동으로 처리되므로 실행하지 않습니다.
            return;
            
            /* 기존 코드 (참고용) - 정산 수집 후 자동 처리로 대체됨
            ...
            */
        } catch (Exception e) {
            log.error("[스케줄러] 비활성화된 메서드 호출: {}", e.getMessage());
        }
    }

    // ========== Helper Methods ==========
    
    /**
     * 번들 내 모든 주문의 상품 매핑 체크
     * 
     * @param orders 번들 내 주문 목록
     * @return 매핑되지 않은 상품 목록 (productId:sku 형식)
     */
    private java.util.List<String> checkBundleProductMappings(List<Order> orders) {
        java.util.List<String> unmappedItems = new java.util.ArrayList<>();
        int totalItemCount = 0;
        int mappedItemCount = 0;
        
        for (Order order : orders) {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                log.warn("[품목 매핑 체크] ⚠️ 상품 아이템 없음: orderId={}", order.getOrderId());
                continue;
            }
            
            totalItemCount += order.getItems().size();
            
            for (OrderItem item : order.getItems()) {

                log.info("[품목 매핑 체크] 🔍 조회 조건: tenantId={}, storeId={}, marketplace={}, productId={}, sku={}",
                  order.getTenantId(),
                  order.getStoreId(),
                  order.getMarketplace(),
                  item.getMarketplaceProductId(),
                  item.getMarketplaceSku());
                  
                  java.util.Optional<com.sellsync.api.domain.mapping.dto.ProductMappingResponse> mapping =
                        productMappingService.findActiveMapping(
                            order.getTenantId(),
                            order.getStoreId(),
                            order.getMarketplace(),
                            item.getMarketplaceProductId(),
                            item.getMarketplaceSku()
                        );
                
                if (mapping.isEmpty()) {
                    String itemKey = String.format("%s:%s", 
                        item.getMarketplaceProductId(), 
                        item.getMarketplaceSku());
                    unmappedItems.add(itemKey);
                    log.warn("[품목 매핑 체크] ❌ 매핑 없음: orderId={}, productId={}, sku={}", 
                        order.getOrderId(), item.getMarketplaceProductId(), item.getMarketplaceSku());
                } else {
                    mappedItemCount++;
                    log.info("[품목 매핑 체크] ✅ 매핑 성공: orderId={}, productId={}, sku={} → erpItemCode={}, mappingStatus={}", 
                        order.getOrderId(), 
                        item.getMarketplaceProductId(), 
                        item.getMarketplaceSku(),
                        mapping.get().getErpItemCode(),
                        mapping.get().getMappingStatus());
                }
            }
        }
        
        // 매핑 체크 통계 로그
        if (!unmappedItems.isEmpty()) {
            log.warn("[품목 매핑 체크 결과] 번들 내 총 상품: {} / 매핑완료: {} / 매핑누락: {} ❌", 
                    totalItemCount, mappedItemCount, unmappedItems.size());
            log.warn("[품목 매핑 체크 결과] 매핑 누락 상품 목록: {}", String.join(", ", unmappedItems));
        } else {
            log.info("[품목 매핑 체크 결과] 번들 내 모든 상품 매핑 완료 ✅ (총 {} 개)", totalItemCount);
        }
        
        return unmappedItems;
    }
    
    private String getErpCredentials(UUID tenantId, String erpCode) {
        // Mock: 실제로는 tenant별 ERP 인증 정보 조회
        // TODO: ErpConfig 테이블 또는 Credential 테이블에서 조회
        return String.format("{\"tenantId\":\"%s\",\"erpCode\":\"%s\",\"apiKey\":\"mock-key\"}", 
            tenantId, erpCode);
    }
}
