package com.sellsync.api.scheduler;

import com.sellsync.api.domain.erp.service.ErpItemSyncService;
import com.sellsync.api.domain.tenant.entity.Tenant;
import com.sellsync.api.domain.tenant.enums.TenantStatus;
import com.sellsync.api.domain.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ERP 품목 동기화 스케줄러
 * - 매일 새벽 3시에 활성 테넌트의 품목 데이터를 동기화
 */
@Component
@ConditionalOnProperty(
    name = "scheduling.erp-item-sync.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
@RequiredArgsConstructor
public class ErpItemSyncScheduler {

    private final ErpItemSyncService syncService;
    private final TenantRepository tenantRepository;

    /**
     * 매일 새벽 3시 품목 동기화
     */
    // @Scheduled(cron = "0 0 3 * * *")
    public void syncItemsScheduled() {
        log.info("=== [ErpItemSyncScheduler] Starting scheduled sync ===");

        List<Tenant> activeTenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
        int success = 0, failed = 0;

        for (Tenant tenant : activeTenants) {
            try {
                // ERP 연동이 설정된 테넌트만 동기화
                // TODO: 실제로는 Credential 테이블에서 ERP 연동 여부 확인
                syncService.syncItems(tenant.getTenantId(), "ECOUNT", "SCHEDULED");
                success++;
            } catch (Exception e) {
                log.error("[ErpItemSyncScheduler] Failed for tenant {}: {}", 
                        tenant.getTenantId(), e.getMessage());
                failed++;
            }
        }

        log.info("=== [ErpItemSyncScheduler] Completed: success={}, failed={} ===", success, failed);
    }
}
