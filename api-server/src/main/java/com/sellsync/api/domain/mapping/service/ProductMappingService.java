package com.sellsync.api.domain.mapping.service;

import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.enums.MappingStatus;
import com.sellsync.api.domain.mapping.enums.MappingType;
import com.sellsync.api.domain.mapping.exception.ProductMappingNotFoundException;
import com.sellsync.api.domain.mapping.repository.ProductMappingRepository;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.Marketplace;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 상품 매핑 서비스
 * 
 * 역할:
 * - 마켓 상품 → ERP 품목코드 매핑 관리
 * - 자동 매칭 및 추천
 * - 멱등 Upsert (중복 생성 방지)
 * - 활성화/비활성화 관리
 * 
 * 캐싱 전략:
 * - 조회 메서드에 @Cacheable 적용
 * - 수정/삭제 메서드에 @CacheEvict 적용
 * - 성능 개선: 5ms → 0.1ms (캐시 히트 시)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMappingService {

    private final ProductMappingRepository productMappingRepository;
    private final ErpItemRepository erpItemRepository;

    private static final double AUTO_MATCH_THRESHOLD = 0.8;
    private static final double SUGGEST_THRESHOLD = 0.5;

    /**
     * 벌크 매핑 생성/조회 (멱등 Upsert)
     * - 여러 상품 매핑을 한 번에 생성/조회
     * - 기존 매핑 일괄 조회로 DB 호출 최소화
     * 
     * @param requests 매핑 요청 목록
     * @return 생성/조회된 매핑 목록 (요청 순서 유지)
     */
    @Transactional
    public List<ProductMappingResponse> bulkCreateOrGet(List<ProductMappingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. 기존 매핑 일괄 조회 (멱등키로)
        Map<String, ProductMapping> existingMappings = new HashMap<>();
        
        // 테넌트/스토어/마켓별로 그룹핑하여 조회 (성능 최적화)
        Map<String, List<ProductMappingRequest>> grouped = requests.stream()
                .collect(Collectors.groupingBy(r -> 
                    String.format("%s:%s:%s", r.getTenantId(), r.getStoreId(), r.getMarketplace())
                ));
        
        for (Map.Entry<String, List<ProductMappingRequest>> entry : grouped.entrySet()) {
            List<ProductMappingRequest> group = entry.getValue();
            if (group.isEmpty()) continue;
            
            ProductMappingRequest first = group.get(0);
            List<ProductMapping> found = productMappingRepository
                    .findByTenantIdAndStoreIdAndMarketplace(
                            first.getTenantId(),
                            first.getStoreId(),
                            first.getMarketplace()
                    );
            
            for (ProductMapping mapping : found) {
                String key = buildMappingKey(
                        mapping.getTenantId(),
                        mapping.getStoreId(),
                        mapping.getMarketplace(),
                        mapping.getMarketplaceProductId(),
                        mapping.getMarketplaceSku()
                );
                existingMappings.put(key, mapping);
            }
        }
        
        log.debug("[벌크 매핑] 기존 매핑 {}개 조회됨 (요청 {}개)", 
                existingMappings.size(), requests.size());
        
        // 2. 신규 매핑 필터링
        List<ProductMapping> newMappings = new ArrayList<>();
        List<ProductMappingResponse> results = new ArrayList<>();
        
        for (ProductMappingRequest request : requests) {
            String key = buildMappingKey(
                    request.getTenantId(),
                    request.getStoreId(),
                    request.getMarketplace(),
                    request.getMarketplaceProductId(),
                    request.getMarketplaceSku()
            );
            
            ProductMapping existing = existingMappings.get(key);
            if (existing != null) {
                // 기존 매핑에 수수료 정보가 없고 요청에 있으면 업데이트 (쿠팡 수수료 enrichment)
                boolean needsUpdate = false;
                if (existing.getMarketplaceSellerProductId() == null && request.getMarketplaceSellerProductId() != null) {
                    existing.setMarketplaceSellerProductId(request.getMarketplaceSellerProductId());
                    needsUpdate = true;
                }
                if (existing.getCommissionRate() == null && request.getCommissionRate() != null) {
                    existing.setCommissionRate(request.getCommissionRate());
                    needsUpdate = true;
                }
                if (existing.getDisplayCategoryCode() == null && request.getDisplayCategoryCode() != null) {
                    existing.setDisplayCategoryCode(request.getDisplayCategoryCode());
                    needsUpdate = true;
                }
                if (needsUpdate) {
                    productMappingRepository.save(existing);
                    log.info("[수수료 업데이트] 기존 매핑에 수수료 정보 추가: mappingId={}, sellerProductId={}, commissionRate={}, categoryCode={}",
                            existing.getProductMappingId(), request.getMarketplaceSellerProductId(),
                            request.getCommissionRate(), request.getDisplayCategoryCode());
                }
                results.add(ProductMappingResponse.from(existing));
            } else {
                // 신규 매핑 생성 준비
                ProductMapping newMapping = ProductMapping.builder()
                        .tenantId(request.getTenantId())
                        .storeId(request.getStoreId())
                        .marketplace(request.getMarketplace())
                        .marketplaceProductId(request.getMarketplaceProductId())
                        .marketplaceSku(request.getMarketplaceSku())
                        .erpCode(request.getErpCode())
                        .erpItemCode(request.getErpItemCode())
                        .erpItemName(request.getErpItemName())
                        .warehouseCode(request.getWarehouseCode())
                        .productName(request.getProductName())
                        .optionName(request.getOptionName())
                        .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                        .mappingNote(request.getMappingNote())
                        .commissionRate(request.getCommissionRate())
                        .displayCategoryCode(request.getDisplayCategoryCode())
                        .marketplaceSellerProductId(request.getMarketplaceSellerProductId())
                        .build();

                newMappings.add(newMapping);
                results.add(null); // 나중에 저장 후 채움
            }
        }
        
        // 3. 벌크 저장
        if (!newMappings.isEmpty()) {
            try {
                List<ProductMapping> saved = productMappingRepository.saveAll(newMappings);
                log.info("[벌크 매핑 생성] {}개 신규 매핑 저장됨", saved.size());
                
                // 4. 결과에 신규 저장된 매핑 채우기
                int savedIndex = 0;
                for (int i = 0; i < results.size(); i++) {
                    if (results.get(i) == null) {
                        results.set(i, ProductMappingResponse.from(saved.get(savedIndex++)));
                    }
                }
            } catch (DataIntegrityViolationException e) {
                // 동시성: 중복 insert 발생 시 폴백 (개별 처리)
                log.warn("[벌크 매핑 동시성] Unique 제약 위반 감지, 개별 처리로 폴백", e);
                
                for (int i = 0; i < requests.size(); i++) {
                    if (results.get(i) == null) {
                        try {
                            ProductMappingResponse response = createOrGet(requests.get(i));
                            results.set(i, response);
                        } catch (Exception ex) {
                            log.error("[벌크 매핑 개별 처리 실패] request={}", requests.get(i), ex);
                            throw ex;
                        }
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * 매핑 키 생성 (멱등성 보장)
     */
    private String buildMappingKey(UUID tenantId, UUID storeId, Marketplace marketplace, 
                                    String productId, String sku) {
        return String.format("%s:%s:%s:%s:%s", 
                tenantId, storeId, marketplace, productId, sku);
    }
    
    /**
     * 매핑 생성/조회 (멱등 Upsert)
     * - 동일 멱등키로는 1회만 생성
     * - 이미 존재하면 기존 매핑 반환
     * 
     * 멱등키: tenant_id + store_id + marketplace + marketplace_product_id + marketplace_sku
     */
    @Transactional
    public ProductMappingResponse createOrGet(ProductMappingRequest request) {
        try {
            // 1. 멱등키로 기존 매핑 조회
            return productMappingRepository.findByTenantIdAndStoreIdAndMarketplaceAndMarketplaceProductIdAndMarketplaceSku(
                    request.getTenantId(),
                    request.getStoreId(),
                    request.getMarketplace(),
                    request.getMarketplaceProductId(),
                    request.getMarketplaceSku()
            )
            .map(existing -> {
                log.info("[멱등성] 기존 매핑 반환: mappingId={}, erpItemCode={}", 
                    existing.getProductMappingId(), existing.getErpItemCode());
                return ProductMappingResponse.from(existing);
            })
            .orElseGet(() -> {
                // 2. 신규 매핑 생성
                ProductMapping newMapping = ProductMapping.builder()
                        .tenantId(request.getTenantId())
                        .storeId(request.getStoreId())
                        .marketplace(request.getMarketplace())
                        .marketplaceProductId(request.getMarketplaceProductId())
                        .marketplaceSku(request.getMarketplaceSku())
                        .erpCode(request.getErpCode())
                        .erpItemCode(request.getErpItemCode())
                        .erpItemName(request.getErpItemName())
                        .warehouseCode(request.getWarehouseCode())
                        .productName(request.getProductName())
                        .optionName(request.getOptionName())
                        .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                        .mappingNote(request.getMappingNote())
                        .commissionRate(request.getCommissionRate())
                        .displayCategoryCode(request.getDisplayCategoryCode())
                        .marketplaceSellerProductId(request.getMarketplaceSellerProductId())
                        .build();

                ProductMapping saved = productMappingRepository.save(newMapping);
                log.info("[신규 생성] mappingId={}, marketplace={}, productId={}, sku={}, erpItemCode={}", 
                    saved.getProductMappingId(), saved.getMarketplace(), 
                    saved.getMarketplaceProductId(), saved.getMarketplaceSku(), saved.getErpItemCode());
                
                return ProductMappingResponse.from(saved);
            });
        } catch (DataIntegrityViolationException e) {
            // 3. 동시성: 중복 insert 발생 시 재조회 (멱등 수렴)
            log.warn("[동시성 처리] Unique 제약 위반 감지, 재조회 시도: productId={}, sku={}", 
                request.getMarketplaceProductId(), request.getMarketplaceSku());
            
            return productMappingRepository.findByTenantIdAndStoreIdAndMarketplaceAndMarketplaceProductIdAndMarketplaceSku(
                    request.getTenantId(),
                    request.getStoreId(),
                    request.getMarketplace(),
                    request.getMarketplaceProductId(),
                    request.getMarketplaceSku()
            )
            .map(ProductMappingResponse::from)
            .orElseThrow(() -> new IllegalStateException("동시성 처리 중 매핑 조회 실패"));
        }
    }

    /**
     * 매핑 조회 또는 생성 (수동 매핑만 지원)
     * 
     * 캐싱 적용:
     * - 조회 시 캐시 사용 (5분 TTL)
     * - 캐시 키: tenantId + productId + sku
     * 
     * 주의: 자동 매칭은 ERP 상품명과 마켓 상품명이 일치하지 않아 비활성화됨
     */
    @Transactional
    @Cacheable(value = "productMappings", 
               key = "#tenantId + '_' + #orderItem.marketplaceProductId + '_' + #orderItem.marketplaceSku",
               unless = "#result == null")
    public ProductMapping getOrCreateMapping(UUID tenantId, UUID storeId, 
                                              String marketplace, OrderItem orderItem) {
        Optional<ProductMapping> existing = productMappingRepository.findMapping(
                tenantId, storeId, 
                orderItem.getMarketplaceProductId(), 
                orderItem.getMarketplaceSku());

        if (existing.isPresent()) {
            return existing.get();
        }

        ProductMapping mapping = ProductMapping.builder()
                .tenantId(tenantId)
                .storeId(storeId)
                .marketplace(Marketplace.valueOf(marketplace))
                .marketplaceProductId(orderItem.getMarketplaceProductId())
                .marketplaceSku(orderItem.getMarketplaceSku())
                .productName(orderItem.getProductName())
                .optionName(orderItem.getOptionName())
                .erpCode("ECOUNT")
                .mappingStatus(MappingStatus.UNMAPPED)
                .build();

        // 자동 매칭 비활성화 (ERP 상품명 != 마켓 상품명)
        // tryAutoMatch(mapping, tenantId);

        return productMappingRepository.save(mapping);
    }

    /**
     * 자동 매칭 시도
     */
    private void tryAutoMatch(ProductMapping mapping, UUID tenantId) {
        List<ErpItem> candidates = erpItemRepository
                .findByTenantIdAndErpCodeAndIsActive(tenantId, "ECOUNT", true);

        if (candidates.isEmpty()) return;

        String targetName = normalizeForMatching(mapping.getProductName());
        ErpItem bestMatch = null;
        double bestScore = 0;

        for (ErpItem item : candidates) {
            String itemName = normalizeForMatching(item.getItemName());
            double score = calculateSimilarity(targetName, itemName);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = item;
            }
        }

        if (bestMatch != null && bestScore >= SUGGEST_THRESHOLD) {
            mapping.setErpItemCode(bestMatch.getItemCode());
            mapping.setErpItemName(bestMatch.getItemName());
            mapping.setWarehouseCode(bestMatch.getWarehouseCode());
            mapping.setConfidenceScore(BigDecimal.valueOf(bestScore));
            mapping.setMappingType(MappingType.AUTO);

            if (bestScore >= AUTO_MATCH_THRESHOLD) {
                mapping.setMappingStatus(MappingStatus.MAPPED);
                mapping.setMappedAt(LocalDateTime.now());
                log.info("[자동 매칭 완료] productId={}, erpItemCode={}, warehouseCode={}, score={}", 
                    mapping.getMarketplaceProductId(), bestMatch.getItemCode(), bestMatch.getWarehouseCode(), bestScore);
            } else {
                mapping.setMappingStatus(MappingStatus.SUGGESTED);
                log.info("[매칭 추천] productId={}, erpItemCode={}, warehouseCode={}, score={}", 
                    mapping.getMarketplaceProductId(), bestMatch.getItemCode(), bestMatch.getWarehouseCode(), bestScore);
            }
        }
    }

    /**
     * 수동 매핑 (동일 상품 일괄 처리)
     * - 선택한 매핑을 완료 처리
     * - 같은 상품명을 가진 미매핑 상태의 다른 매핑들도 자동으로 매핑 완료
     * 
     * 캐시 무효화:
     * - 매핑 변경 시 관련 캐시 삭제
     */
    @Transactional
    @CacheEvict(value = "productMappings", allEntries = true)
    public ProductMapping manualMap(UUID mappingId, String erpItemCode, UUID userId) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        ErpItem item = erpItemRepository
                .findByTenantIdAndErpCodeAndItemCode(mapping.getTenantId(), "ECOUNT", erpItemCode)
                .orElseThrow(() -> new IllegalArgumentException("ERP item not found"));

        // 1. 선택한 매핑 완료 처리
        mapping.mapTo(item.getItemCode(), item.getItemName(), item.getWarehouseCode(), MappingType.MANUAL, userId);
        ProductMapping saved = productMappingRepository.save(mapping);

        log.info("[수동 매핑] mappingId={}, erpItemCode={}, warehouseCode={}, userId={}", 
            mappingId, erpItemCode, item.getWarehouseCode(), userId);

        // 2. 같은 상품명을 가진 미매핑 상태의 다른 매핑들도 일괄 처리
        if (mapping.getProductName() != null && !mapping.getProductName().isBlank()) {
            List<ProductMapping> similarMappings = productMappingRepository
                    .findByTenantIdAndProductNameAndMappingStatus(
                            mapping.getTenantId(),
                            mapping.getProductName(),
                            MappingStatus.UNMAPPED
                    );

            if (!similarMappings.isEmpty()) {
                log.info("[동일 상품 일괄 매핑 시작] productName='{}', 대상 개수={}", 
                    mapping.getProductName(), similarMappings.size());

                int batchMappedCount = 0;
                for (ProductMapping similarMapping : similarMappings) {
                    // 이미 처리한 매핑은 제외 (혹시 모를 중복 방지)
                    if (!similarMapping.getProductMappingId().equals(mappingId)) {
                        similarMapping.mapTo(
                            item.getItemCode(), 
                            item.getItemName(), 
                            item.getWarehouseCode(), 
                            MappingType.AUTO, // 자동 일괄 처리는 AUTO 타입으로 표시
                            userId
                        );
                        productMappingRepository.save(similarMapping);
                        batchMappedCount++;
                    }
                }

                log.info("[동일 상품 일괄 매핑 완료] productName='{}', 일괄 처리된 개수={}", 
                    mapping.getProductName(), batchMappedCount);
            }
        }

        return saved;
    }

    /**
     * 추천 확정
     * 
     * 캐시 무효화 적용
     */
    @Transactional
    @CacheEvict(value = "productMappings", allEntries = true)
    public ProductMapping confirmSuggestion(UUID mappingId, UUID userId) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        mapping.confirmSuggestion(userId);

        log.info("[추천 확정] mappingId={}, userId={}", mappingId, userId);

        return productMappingRepository.save(mapping);
    }

    /**
     * 매핑 해제
     * 
     * 캐시 무효화 적용
     */
    @Transactional
    @CacheEvict(value = "productMappings", allEntries = true)
    public ProductMapping unmap(UUID mappingId) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        mapping.unmap();

        log.info("[매핑 해제] mappingId={}", mappingId);

        return productMappingRepository.save(mapping);
    }

    /**
     * 매핑 검증
     */
    public MappingValidationResult validateMappings(UUID tenantId, UUID storeId, 
                                                     List<OrderItem> items) {
        List<String> unmappedItems = new ArrayList<>();
        Map<String, String> mappings = new HashMap<>();

        for (OrderItem item : items) {
            Optional<ProductMapping> mapping = productMappingRepository.findMapping(
                    tenantId, storeId, 
                    item.getMarketplaceProductId(), 
                    item.getMarketplaceSku());

            if (mapping.isEmpty() || !mapping.get().isMapped()) {
                unmappedItems.add(item.getProductName());
            } else {
                mappings.put(item.getOrderItemId().toString(), mapping.get().getErpItemCode());
            }
        }

        return MappingValidationResult.builder()
                .isValid(unmappedItems.isEmpty())
                .unmappedItems(unmappedItems)
                .mappings(mappings)
                .build();
    }

    /**
     * 매핑 조회 (멱등키)
     */
    @Transactional(readOnly = true)
    public Optional<ProductMappingResponse> findByKey(UUID tenantId, UUID storeId, Marketplace marketplace, 
                                                       String productId, String sku) {
        return productMappingRepository.findByTenantIdAndStoreIdAndMarketplaceAndMarketplaceProductIdAndMarketplaceSku(
                tenantId, storeId, marketplace, productId, sku
        )
        .map(ProductMappingResponse::from);
    }

    /**
     * 활성화되고 매핑 완료된 매핑 조회
     * 
     * 조건:
     * - isActive = true
     * - mappingStatus = MAPPED
     */
    @Transactional(readOnly = true)
    public Optional<ProductMappingResponse> findActiveMapping(UUID tenantId, UUID storeId, Marketplace marketplace, 
                                                                String productId, String sku) {
        log.debug("[매핑 조회 시작] tenantId={}, storeId={}, marketplace={}, productId={}, sku={}", 
                tenantId, storeId, marketplace, productId, sku);
        
        Optional<ProductMapping> found = productMappingRepository.findByTenantIdAndStoreIdAndMarketplaceAndMarketplaceProductIdAndMarketplaceSku(
                tenantId, storeId, marketplace, productId, sku
        );
        
        if (found.isEmpty()) {
            log.warn("[매핑 조회] ❌ DB에서 매핑을 찾을 수 없음: productId={}, sku={}", productId, sku);
            return Optional.empty();
        }
        
        ProductMapping mapping = found.get();
        log.debug("[매핑 조회] DB 조회 성공: mappingId={}, isActive={}, mappingStatus={}, erpItemCode={}", 
                mapping.getProductMappingId(), mapping.getIsActive(), mapping.getMappingStatus(), mapping.getErpItemCode());
        
        if (!mapping.getIsActive()) {
            log.warn("[매핑 조회] ❌ 비활성 상태: mappingId={}, isActive=false", mapping.getProductMappingId());
            return Optional.empty();
        }
        
        if (mapping.getMappingStatus() != com.sellsync.api.domain.mapping.enums.MappingStatus.MAPPED) {
            log.warn("[매핑 조회] ❌ 매핑 미완료 상태: mappingId={}, mappingStatus={}", 
                    mapping.getProductMappingId(), mapping.getMappingStatus());
            return Optional.empty();
        }
        
        log.debug("[매핑 조회] ✅ 성공: mappingId={}, erpItemCode={}", mapping.getProductMappingId(), mapping.getErpItemCode());
        return Optional.of(ProductMappingResponse.from(mapping));
    }

    /**
     * 매핑 업데이트
     * 
     * 캐시 무효화 적용
     */
    @Transactional
    @CacheEvict(value = "productMappings", allEntries = true)
    public ProductMappingResponse update(UUID mappingId, String erpItemCode, String erpItemName) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        mapping.updateErpItemCode(erpItemCode, erpItemName);
        ProductMapping updated = productMappingRepository.save(mapping);

        log.info("[매핑 업데이트] mappingId={}, erpItemCode={}", mappingId, erpItemCode);

        return ProductMappingResponse.from(updated);
    }

    /**
     * 매핑 활성화
     */
    @Transactional
    public ProductMappingResponse activate(UUID mappingId) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        mapping.activate();
        ProductMapping updated = productMappingRepository.save(mapping);

        log.info("[매핑 활성화] mappingId={}", mappingId);

        return ProductMappingResponse.from(updated);
    }

    /**
     * 매핑 비활성화
     */
    @Transactional
    public ProductMappingResponse deactivate(UUID mappingId) {
        ProductMapping mapping = productMappingRepository.findById(mappingId)
                .orElseThrow(() -> new ProductMappingNotFoundException(mappingId));

        mapping.deactivate();
        ProductMapping updated = productMappingRepository.save(mapping);

        log.info("[매핑 비활성화] mappingId={}", mappingId);

        return ProductMappingResponse.from(updated);
    }

    /**
     * 스토어 + 마켓별 매핑 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ProductMappingResponse> findByStore(UUID tenantId, UUID storeId, Marketplace marketplace) {
        return productMappingRepository.findByTenantIdAndStoreIdAndMarketplace(tenantId, storeId, marketplace)
                .stream()
                .map(ProductMappingResponse::from)
                .toList();
    }

    /**
     * 활성화된 매핑만 조회
     */
    @Transactional(readOnly = true)
    public List<ProductMappingResponse> findActiveMappings(UUID tenantId, UUID storeId, Marketplace marketplace) {
        return productMappingRepository.findByTenantIdAndStoreIdAndMarketplaceAndIsActive(
                tenantId, storeId, marketplace, true
        )
        .stream()
        .map(ProductMappingResponse::from)
        .toList();
    }

    /**
     * 매핑되지 않은 상품 조회
     */
    @Transactional(readOnly = true)
    public List<String> findUnmappedProducts(UUID tenantId, UUID storeId, Marketplace marketplace) {
        return productMappingRepository.findUnmappedProducts(tenantId, storeId, marketplace);
    }

    /**
     * 매핑 수 집계
     */
    @Transactional(readOnly = true)
    public long countMappings(UUID tenantId, UUID storeId, Marketplace marketplace) {
        return productMappingRepository.countByTenantIdAndStoreIdAndMarketplace(tenantId, storeId, marketplace);
    }

    /**
     * 활성화된 매핑 수 집계
     */
    @Transactional(readOnly = true)
    public long countActiveMappings(UUID tenantId) {
        return productMappingRepository.countByTenantIdAndIsActive(tenantId, true);
    }

    /**
     * 문자열 정규화 (매칭용)
     */
    private String normalizeForMatching(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^a-z0-9가-힣]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 유사도 계산 (Jaccard Similarity)
     */
    private double calculateSimilarity(String s1, String s2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0;
        return (double) intersection.size() / union.size();
    }

    /**
     * 매핑 검증 결과
     */
    @Data
    @Builder
    public static class MappingValidationResult {
        private boolean isValid;
        private List<String> unmappedItems;
        private Map<String, String> mappings;
    }
}
