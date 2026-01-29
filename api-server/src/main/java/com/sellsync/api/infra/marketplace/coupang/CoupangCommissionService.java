package com.sellsync.api.infra.marketplace.coupang;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 쿠팡 수수료 조회 서비스
 *
 * 쿠팡 상품 조회 API를 통해 수수료율(saleAgentCommission)과 카테고리코드(displayCategoryCode)를 조회합니다.
 * Caffeine 인메모리 캐시를 사용하여 동일 sellerProductId에 대한 중복 API 호출을 방지합니다.
 *
 * 캐시 전략:
 * - TTL: 24시간 (수수료율은 자주 변경되지 않음)
 * - 최대 5000건 (sellerProductId 기준)
 * - API 실패 시 graceful degradation (수수료 없이 진행)
 */
@Service
@Slf4j
public class CoupangCommissionService {

    private final CoupangProductClient productClient;

    /**
     * sellerProductId → CoupangProductInfo 캐시
     * 24시간 TTL, 최대 5000건
     */
    private final Cache<String, Optional<CoupangProductInfo>> productInfoCache;

    public CoupangCommissionService(CoupangProductClient productClient) {
        this.productClient = productClient;
        this.productInfoCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /**
     * 쿠팡 상품 수수료 정보 조회 (캐시 우선)
     *
     * @param credentials 쿠팡 API 인증 JSON
     * @param sellerProductId 쿠팡 sellerProductId
     * @return 상품 수수료 정보 (캐시 히트 또는 API 호출)
     */
    public Optional<CoupangProductInfo> getProductInfo(String credentials, String sellerProductId) {
        if (sellerProductId == null || sellerProductId.isEmpty()) {
            return Optional.empty();
        }

        return productInfoCache.get(sellerProductId, key -> {
            log.info("[CoupangCommission] 캐시 미스 - API 호출: sellerProductId={}", key);
            try {
                Optional<CoupangProductInfo> result = productClient.fetchProductInfo(credentials, key);
                log.info("[CoupangCommission] API 결과: sellerProductId={}, success={}, commissionRate={}",
                        key, result.isPresent(),
                        result.map(CoupangProductInfo::getSaleAgentCommission).orElse(null));
                return result;
            } catch (Exception e) {
                log.error("[CoupangCommission] 상품 정보 조회 실패 - sellerProductId={}, error={}",
                        key, e.getMessage(), e);
                return Optional.empty();
            }
        });
    }

    /**
     * 상품 정보 캐시 전체 무효화
     * 카테고리 동기화 후 호출하여 수수료율이 재조회되도록 함
     */
    public void invalidateAllCache() {
        productInfoCache.invalidateAll();
        log.info("[CoupangCommission] 상품 정보 캐시 전체 무효화 완료");
    }
}
