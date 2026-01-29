package com.sellsync.api.infra.marketplace.coupang;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 쿠팡 카테고리 동기화 + 수수료 자동 매핑 서비스
 *
 * Phase 1: API에서 전체 display 카테고리 조회 → DB 저장
 * Phase 2: 카테고리 한글명 ↔ 수수료 참조 테이블 자동 매칭 → 브릿지 테이블 생성
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CoupangCategorySyncService {

    /**
     * API 카테고리 root 이름 → 수수료 테이블 major_category 이름 별칭
     */
    static final Map<String, String> ROOT_NAME_ALIASES = Map.of(
            "가구/홈데코", "가구/홈인테리어",
            "가전/디지털", "가전디지털",
            "문구/오피스", "문구/사무용품",
            "스포츠/레져", "스포츠/레저용품",
            "음반/DVD", "음반",
            "출산/유아동", "출산/유아",
            "패션의류잡화", "패션"
    );

    private final CoupangCategoryClient categoryClient;
    private final CoupangDisplayCategoryRepository displayCategoryRepository;
    private final CoupangCommissionRateRepository commissionRateRepository;
    private final CoupangCategoryCodeMappingRepository mappingRepository;

    /**
     * 카테고리 동기화 + 자동 매핑 실행
     */
    @Transactional
    public SyncResult syncAndAutoMap(String credentials) {
        log.info("[CoupangCategorySync] 동기화 시작");

        // Phase 1: 카테고리 동기화
        int savedCount = syncDisplayCategories(credentials);

        // Phase 2: 자동 매핑
        int mappedCount = autoMapCategoriesToCommissionRates();

        log.info("[CoupangCategorySync] 동기화 완료: 카테고리 {}개 저장, 매핑 {}개 생성",
                savedCount, mappedCount);
        return SyncResult.builder()
                .savedCount(savedCount)
                .mappedCount(mappedCount)
                .build();
    }

    /**
     * Phase 1: API에서 카테고리 조회 → DB 저장
     */
    private int syncDisplayCategories(String credentials) {
        List<CoupangCategoryClient.DisplayCategoryDto> categories =
                categoryClient.fetchAllDisplayCategories(credentials);

        log.info("[CoupangCategorySync] API에서 카테고리 {}개 조회", categories.size());

        // 기존 코드 조회 (중복 방지)
        Set<String> existingCodes = displayCategoryRepository.findAll().stream()
                .map(CoupangDisplayCategory::getDisplayCategoryCode)
                .collect(Collectors.toSet());

        List<CoupangDisplayCategory> toSave = new ArrayList<>();
        for (var dto : categories) {
            if (!existingCodes.contains(dto.getDisplayCategoryCode())) {
                toSave.add(CoupangDisplayCategory.builder()
                        .displayCategoryCode(dto.getDisplayCategoryCode())
                        .displayCategoryName(dto.getDisplayCategoryName())
                        .parentCategoryCode(dto.getParentCategoryCode())
                        .depth(dto.getDepth())
                        .build());
            }
        }

        // 배치 저장
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += 100) {
                int end = Math.min(i + 100, toSave.size());
                displayCategoryRepository.saveAll(toSave.subList(i, end));
                displayCategoryRepository.flush();
            }
        }

        log.info("[CoupangCategorySync] 신규 카테고리 {}개 저장 (전체 {}개 중)",
                toSave.size(), categories.size());
        return toSave.size();
    }

    /**
     * Phase 2: 카테고리 한글명 → 수수료 참조 테이블 자동 매칭
     */
    private int autoMapCategoriesToCommissionRates() {
        // 1. 수수료 참조 테이블 메모리 로드 (majorCategory 기준 그룹핑)
        List<CoupangCommissionRate> allRates = commissionRateRepository.findAll();
        Map<String, List<CoupangCommissionRate>> ratesByMajor = allRates.stream()
                .collect(Collectors.groupingBy(CoupangCommissionRate::getMajorCategory));

        // 2. 전체 카테고리 메모리 로드 (code → entity)
        List<CoupangDisplayCategory> allCategories = displayCategoryRepository.findAll();
        Map<String, CoupangDisplayCategory> categoryByCode = allCategories.stream()
                .collect(Collectors.toMap(
                        CoupangDisplayCategory::getDisplayCategoryCode, c -> c,
                        (existing, replacement) -> existing));

        // 3. 이미 매핑된 코드 조회
        Set<String> alreadyMappedCodes = mappingRepository.findAll().stream()
                .map(CoupangCategoryCodeMapping::getDisplayCategoryCode)
                .collect(Collectors.toSet());

        // 4. 각 카테고리별 자동 매칭
        int mappedCount = 0;
        List<CoupangCategoryCodeMapping> newMappings = new ArrayList<>();

        for (CoupangDisplayCategory category : allCategories) {
            if (alreadyMappedCodes.contains(category.getDisplayCategoryCode())) {
                continue;
            }

            // 조상 경로 구성
            List<String> ancestryNames = getAncestryPath(category, categoryByCode);
            if (ancestryNames.isEmpty()) continue;

            // 수수료율 매칭
            CoupangCommissionRate matchedRate = findBestMatch(ancestryNames, ratesByMajor);

            if (matchedRate != null) {
                newMappings.add(CoupangCategoryCodeMapping.builder()
                        .displayCategoryCode(category.getDisplayCategoryCode())
                        .commissionRate(matchedRate)
                        .memo("자동 매핑: " + String.join(" > ", ancestryNames))
                        .build());
                mappedCount++;
            }
        }

        // 배치 저장
        if (!newMappings.isEmpty()) {
            for (int i = 0; i < newMappings.size(); i += 100) {
                int end = Math.min(i + 100, newMappings.size());
                mappingRepository.saveAll(newMappings.subList(i, end));
                mappingRepository.flush();
            }
        }

        log.info("[CoupangCategorySync] 자동 매핑 완료: {}개 매핑 생성 (전체 카테고리 {}개 중)",
                mappedCount, allCategories.size());
        return mappedCount;
    }

    /**
     * 카테고리 트리를 위로 타고 올라가며 조상 경로 구성
     * 반환: [root, ..., leaf] 순서의 카테고리명 리스트
     */
    List<String> getAncestryPath(CoupangDisplayCategory category,
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
     * 조상 경로를 수수료 참조 테이블과 매칭
     *
     * 우선순위:
     * 1. major + middle + minor 정확 매칭
     * 2. major + middle(minor=null) 매칭
     * 3. 조상 이름 중 minor_category 매칭
     * 4. 조상 이름 중 middle_category 매칭
     * 5. 해당 major의 "기본 수수료" 폴백
     */
    CoupangCommissionRate findBestMatch(List<String> ancestryNames,
                                         Map<String, List<CoupangCommissionRate>> ratesByMajor) {
        if (ancestryNames.isEmpty()) return null;

        String rootName = ancestryNames.get(0);

        // major_category 매칭 (별칭 적용)
        List<CoupangCommissionRate> majorRates = ratesByMajor.get(rootName);
        if (majorRates == null || majorRates.isEmpty()) {
            String alias = ROOT_NAME_ALIASES.get(rootName);
            if (alias != null) {
                majorRates = ratesByMajor.get(alias);
            }
        }
        if (majorRates == null || majorRates.isEmpty()) {
            return null;
        }

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
                        .filter(r -> name.equals(r.getMiddleCategory())
                                && r.getMinorCategory() == null)
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

        // 4. 조상 이름 중 middle_category 매칭 (minor 무관)
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
        Optional<CoupangCommissionRate> defaultRate = majorRates.stream()
                .filter(r -> "기본 수수료".equals(r.getMiddleCategory()))
                .findFirst();

        if (defaultRate.isPresent()) {
            return defaultRate.get();
        }

        log.warn("[CoupangCategorySync] 매칭 불가: path={}", String.join(" > ", ancestryNames));
        return null;
    }

    @Data
    @Builder
    public static class SyncResult {
        private int savedCount;
        private int mappedCount;
    }
}
