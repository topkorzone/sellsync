package com.sellsync.api.infra.marketplace.coupang;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 쿠팡 수수료율 조회 서비스
 *
 * displayCategoryCode → DB 매핑 테이블 조회 → 수수료율 반환 (미매핑 시 null)
 * Caffeine 캐시 사용 (1시간 TTL, 최대 1000건)
 */
@Service
@Slf4j
public class CoupangCommissionRateService {

    private final CoupangCategoryCodeMappingRepository mappingRepository;
    private final CoupangCommissionRateRepository rateRepository;
    private final CoupangDisplayCategoryRepository displayCategoryRepository;

    private final Cache<String, Optional<BigDecimal>> rateCache;

    public CoupangCommissionRateService(
            CoupangCategoryCodeMappingRepository mappingRepository,
            CoupangCommissionRateRepository rateRepository,
            CoupangDisplayCategoryRepository displayCategoryRepository) {
        this.mappingRepository = mappingRepository;
        this.rateRepository = rateRepository;
        this.displayCategoryRepository = displayCategoryRepository;
        this.rateCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /**
     * displayCategoryCode로 수수료율 조회
     *
     * 1) 캐시 확인
     * 2) DB 매핑 테이블 조회 → effectiveRate
     * 3) 매핑 없음 → null 반환 + 미매핑 코드 WARN 로그
     */
    public BigDecimal getCommissionRate(String displayCategoryCode) {
        if (displayCategoryCode == null || displayCategoryCode.isBlank()) {
            log.warn("[CoupangCommissionRate] displayCategoryCode가 null/빈값 → 수수료율 없음");
            return null;
        }

        return rateCache.get(displayCategoryCode, this::lookupRate).orElse(null);
    }

    private Optional<BigDecimal> lookupRate(String displayCategoryCode) {
        // 1. 브릿지 테이블 조회
        Optional<CoupangCategoryCodeMapping> mapping = mappingRepository.findByDisplayCategoryCode(displayCategoryCode);

        if (mapping.isPresent()) {
            BigDecimal effectiveRate = mapping.get().getEffectiveRate();
            if (effectiveRate != null) {
                log.info("[CoupangCommissionRate] DB 매핑 조회 성공: displayCategoryCode={}, rate={}",
                        displayCategoryCode, effectiveRate);
                return Optional.of(effectiveRate);
            }
        }

        // 2. 브릿지 테이블에 없으면 카테고리 트리 기반 자동 매칭 시도
        Optional<BigDecimal> autoMatched = tryAutoMatchFromCategoryTree(displayCategoryCode);
        if (autoMatched.isPresent()) {
            return autoMatched;
        }

        log.warn("[CoupangCommissionRate] 미매핑 카테고리 코드: displayCategoryCode={} → 수수료율 미설정 (매핑 필요)",
                displayCategoryCode);
        return Optional.empty();
    }

    /**
     * 카테고리 트리 기반 실시간 자동 매칭 (write-through)
     * 매칭 성공 시 브릿지 테이블에 저장하여 다음 조회에서 캐시 히트
     */
    private Optional<BigDecimal> tryAutoMatchFromCategoryTree(String displayCategoryCode) {
        Optional<CoupangDisplayCategory> categoryOpt =
                displayCategoryRepository.findByDisplayCategoryCode(displayCategoryCode);
        if (categoryOpt.isEmpty()) return Optional.empty();

        // 전체 카테고리 로드 (트리 탐색용)
        Map<String, CoupangDisplayCategory> categoryByCode = displayCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CoupangDisplayCategory::getDisplayCategoryCode, c -> c,
                        (existing, replacement) -> existing));

        // 조상 경로 구성
        List<String> ancestryNames = getAncestryPath(categoryOpt.get(), categoryByCode);
        if (ancestryNames.isEmpty()) return Optional.empty();

        // 수수료 참조 테이블에서 매칭
        String rootName = ancestryNames.get(0);
        String resolvedName = CoupangCategorySyncService.ROOT_NAME_ALIASES.getOrDefault(rootName, rootName);
        List<CoupangCommissionRate> majorRates = rateRepository.findByMajorCategory(resolvedName);
        if (majorRates.isEmpty()) return Optional.empty();

        Map<String, List<CoupangCommissionRate>> ratesByMajor = new HashMap<>();
        ratesByMajor.put(rootName, majorRates);

        CoupangCommissionRate matched = findBestMatch(ancestryNames, ratesByMajor);
        if (matched == null) return Optional.empty();

        // write-through: 브릿지 테이블에 저장
        try {
            CoupangCategoryCodeMapping newMapping = CoupangCategoryCodeMapping.builder()
                    .displayCategoryCode(displayCategoryCode)
                    .commissionRate(matched)
                    .memo("자동 매핑 (실시간): " + String.join(" > ", ancestryNames))
                    .build();
            mappingRepository.save(newMapping);
            log.info("[CoupangCommissionRate] 실시간 자동 매핑 저장: displayCategoryCode={}, rate={}, path={}",
                    displayCategoryCode, matched.getCommissionRate(), String.join(" > ", ancestryNames));
        } catch (Exception e) {
            log.warn("[CoupangCommissionRate] 자동 매핑 저장 실패 (수수료율은 반환): {}", e.getMessage());
        }

        return Optional.of(matched.getCommissionRate());
    }

    /**
     * 카테고리 트리 조상 경로 구성 [root, ..., leaf]
     */
    private List<String> getAncestryPath(CoupangDisplayCategory category,
                                          Map<String, CoupangDisplayCategory> categoryByCode) {
        LinkedList<String> path = new LinkedList<>();
        CoupangDisplayCategory current = category;
        Set<String> visited = new HashSet<>();

        while (current != null) {
            if (visited.contains(current.getDisplayCategoryCode())) break;
            visited.add(current.getDisplayCategoryCode());

            path.addFirst(current.getDisplayCategoryName());

            if (current.getParentCategoryCode() == null) break;
            current = categoryByCode.get(current.getParentCategoryCode());
        }

        return path;
    }

    /**
     * 조상 경로 → 수수료 참조 테이블 매칭 (CoupangCategorySyncService와 동일 로직)
     */
    private CoupangCommissionRate findBestMatch(List<String> ancestryNames,
                                                 Map<String, List<CoupangCommissionRate>> ratesByMajor) {
        if (ancestryNames.isEmpty()) return null;

        String rootName = ancestryNames.get(0);
        List<CoupangCommissionRate> majorRates = ratesByMajor.get(rootName);
        if (majorRates == null || majorRates.isEmpty()) {
            String alias = CoupangCategorySyncService.ROOT_NAME_ALIASES.get(rootName);
            if (alias != null) {
                majorRates = ratesByMajor.get(alias);
            }
        }
        if (majorRates == null || majorRates.isEmpty()) return null;

        // 1. major + middle + minor 정확 매칭
        if (ancestryNames.size() >= 3) {
            for (int i = 1; i < ancestryNames.size() - 1; i++) {
                String middleName = ancestryNames.get(i);
                String leafName = ancestryNames.get(ancestryNames.size() - 1);
                Optional<CoupangCommissionRate> exact = majorRates.stream()
                        .filter(r -> middleName.equals(r.getMiddleCategory())
                                && leafName.equals(r.getMinorCategory()))
                        .findFirst();
                if (exact.isPresent()) return exact.get();
            }
        }

        // 2. major + middle(minor=null) 매칭
        if (ancestryNames.size() >= 2) {
            for (int i = 1; i < ancestryNames.size(); i++) {
                String name = ancestryNames.get(i);
                Optional<CoupangCommissionRate> middleMatch = majorRates.stream()
                        .filter(r -> name.equals(r.getMiddleCategory()) && r.getMinorCategory() == null)
                        .findFirst();
                if (middleMatch.isPresent()) return middleMatch.get();
            }
        }

        // 3. 조상 이름 중 minor_category 매칭
        if (ancestryNames.size() >= 2) {
            for (int i = ancestryNames.size() - 1; i >= 1; i--) {
                String name = ancestryNames.get(i);
                Optional<CoupangCommissionRate> minorMatch = majorRates.stream()
                        .filter(r -> name.equals(r.getMinorCategory()))
                        .findFirst();
                if (minorMatch.isPresent()) return minorMatch.get();
            }
        }

        // 4. 조상 이름 중 middle_category 매칭
        if (ancestryNames.size() >= 2) {
            for (int i = 1; i < ancestryNames.size(); i++) {
                String name = ancestryNames.get(i);
                Optional<CoupangCommissionRate> middleOnly = majorRates.stream()
                        .filter(r -> name.equals(r.getMiddleCategory()))
                        .findFirst();
                if (middleOnly.isPresent()) return middleOnly.get();
            }
        }

        // 5. "기본 수수료" 폴백
        return majorRates.stream()
                .filter(r -> "기본 수수료".equals(r.getMiddleCategory()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 참조 테이블 전체 조회
     */
    public List<CoupangCommissionRate> getAllReferenceRates() {
        return rateRepository.findAll();
    }

    /**
     * 매핑 추가/수정
     */
    @Transactional
    public CoupangCategoryCodeMapping addOrUpdateMapping(
            String displayCategoryCode, UUID commissionRateId, BigDecimal overrideRate, String memo) {

        CoupangCategoryCodeMapping mapping = mappingRepository.findByDisplayCategoryCode(displayCategoryCode)
                .orElse(CoupangCategoryCodeMapping.builder()
                        .displayCategoryCode(displayCategoryCode)
                        .build());

        if (commissionRateId != null) {
            CoupangCommissionRate rate = rateRepository.findById(commissionRateId)
                    .orElseThrow(() -> new IllegalArgumentException("수수료 참조 데이터 없음: " + commissionRateId));
            mapping.setCommissionRate(rate);
        }

        mapping.setOverrideRate(overrideRate);
        mapping.setMemo(memo);

        CoupangCategoryCodeMapping saved = mappingRepository.save(mapping);

        // 캐시 무효화
        rateCache.invalidate(displayCategoryCode);
        log.info("[CoupangCommissionRate] 매핑 저장 완료: displayCategoryCode={}, commissionRateId={}, overrideRate={}, memo={}",
                displayCategoryCode, commissionRateId, overrideRate, memo);

        return saved;
    }

    /**
     * 전체 캐시 무효화 (동기화 후 호출)
     */
    public void invalidateAllCache() {
        rateCache.invalidateAll();
        log.info("[CoupangCommissionRate] 전체 캐시 무효화 완료");
    }
}
