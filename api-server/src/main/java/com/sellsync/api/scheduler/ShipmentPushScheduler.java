package com.sellsync.api.scheduler;

import com.sellsync.api.domain.shipping.service.ShipmentPushService;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 송장 반영 스케줄러
 * 
 * 스케줄:
 * - 5분마다 대기 중인 송장 반영
 * - 1시간마다 실패한 송장 재시도
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentPushScheduler {

    private final ShipmentPushService pushService;
    private final StoreRepository storeRepository;

    /**
     * 대기 중인 송장 반영 (5분 주기)
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void pushPendingShipments() {
        log.info("=== [ShipmentPushScheduler] 대기 송장 반영 시작 ===");

        try {
            // 활성 상점 목록 조회
            List<Store> activeStores = storeRepository.findAll().stream()
                    .filter(Store::getIsActive)
                    .collect(Collectors.toList());

            // 테넌트별로 그룹핑
            List<UUID> tenantIds = activeStores.stream()
                    .map(Store::getTenantId)
                    .distinct()
                    .collect(Collectors.toList());

            int totalSuccess = 0;
            for (UUID tenantId : tenantIds) {
                try {
                    int success = pushService.pushPendingShipments(tenantId);
                    totalSuccess += success;
                    
                    if (success > 0) {
                        log.info("[ShipmentPushScheduler] 테넌트 {} 송장 반영 완료: {} 건", tenantId, success);
                    }
                } catch (Exception e) {
                    log.error("[ShipmentPushScheduler] 테넌트 {} 송장 반영 실패", tenantId, e);
                }
            }

            log.info("=== [ShipmentPushScheduler] 대기 송장 반영 완료: 총 {} 건 ===", totalSuccess);
            
        } catch (Exception e) {
            log.error("[ShipmentPushScheduler] 대기 송장 반영 스케줄러 오류", e);
        }
    }

    /**
     * 실패한 송장 재시도 (1시간 주기)
     */
    @Scheduled(cron = "0 0 * * * *")
    public void retryFailedShipments() {
        log.info("=== [ShipmentPushScheduler] 실패 송장 재시도 시작 ===");

        try {
            // 활성 상점 목록 조회
            List<Store> activeStores = storeRepository.findAll().stream()
                    .filter(Store::getIsActive)
                    .collect(Collectors.toList());

            // 테넌트별로 그룹핑
            List<UUID> tenantIds = activeStores.stream()
                    .map(Store::getTenantId)
                    .distinct()
                    .collect(Collectors.toList());

            int totalSuccess = 0;
            for (UUID tenantId : tenantIds) {
                try {
                    int success = pushService.retryFailedShipments(tenantId);
                    totalSuccess += success;
                    
                    if (success > 0) {
                        log.info("[ShipmentPushScheduler] 테넌트 {} 재시도 완료: {} 건", tenantId, success);
                    }
                } catch (Exception e) {
                    log.error("[ShipmentPushScheduler] 테넌트 {} 재시도 실패", tenantId, e);
                }
            }

            log.info("=== [ShipmentPushScheduler] 실패 송장 재시도 완료: 총 {} 건 ===", totalSuccess);
            
        } catch (Exception e) {
            log.error("[ShipmentPushScheduler] 재시도 스케줄러 오류", e);
        }
    }
}
