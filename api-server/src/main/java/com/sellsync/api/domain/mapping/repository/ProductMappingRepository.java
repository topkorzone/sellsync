package com.sellsync.api.domain.mapping.repository;

import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.enums.MappingStatus;
import com.sellsync.api.domain.order.enums.Marketplace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ProductMapping Repository
 */
@Repository
public interface ProductMappingRepository extends JpaRepository<ProductMapping, UUID> {

    /**
     * 멱등성 키로 매핑 조회
     * Key: tenant_id + store_id + marketplace + marketplace_product_id + marketplace_sku
     */
    Optional<ProductMapping> findByTenantIdAndStoreIdAndMarketplaceAndMarketplaceProductIdAndMarketplaceSku(
        UUID tenantId,
        UUID storeId,
        Marketplace marketplace,
        String marketplaceProductId,
        String marketplaceSku
    );

    /**
     * 테넌트 + 스토어 + 마켓별 매핑 목록 조회
     */
    List<ProductMapping> findByTenantIdAndStoreIdAndMarketplace(
        UUID tenantId,
        UUID storeId,
        Marketplace marketplace
    );

    /**
     * 테넌트 + 스토어 + 마켓별 활성화된 매핑 목록 조회
     */
    List<ProductMapping> findByTenantIdAndStoreIdAndMarketplaceAndIsActive(
        UUID tenantId,
        UUID storeId,
        Marketplace marketplace,
        Boolean isActive
    );

    /**
     * 테넌트 + 활성화 여부로 조회 (페이징)
     */
    Page<ProductMapping> findByTenantIdAndIsActiveOrderByCreatedAtDesc(
        UUID tenantId,
        Boolean isActive,
        Pageable pageable
    );

    /**
     * ERP 품목코드로 역조회
     */
    @Query("SELECT pm FROM ProductMapping pm " +
           "WHERE pm.tenantId = :tenantId " +
           "AND pm.erpCode = :erpCode " +
           "AND pm.erpItemCode = :erpItemCode")
    List<ProductMapping> findByErpItemCode(
        @Param("tenantId") UUID tenantId,
        @Param("erpCode") String erpCode,
        @Param("erpItemCode") String erpItemCode
    );

    /**
     * 마켓 상품 ID로 조회 (스토어 무관, 전체 검색)
     */
    List<ProductMapping> findByMarketplaceAndMarketplaceProductId(
        Marketplace marketplace,
        String marketplaceProductId
    );

    /**
     * 테넌트 + 마켓 상품 ID + SKU로 조회
     */
    @Query("SELECT m FROM ProductMapping m WHERE m.tenantId = :tenantId " +
           "AND m.marketplaceProductId = :productId " +
           "AND (m.marketplaceSku = :sku OR (m.marketplaceSku IS NULL AND :sku IS NULL)) " +
           "AND m.isActive = TRUE " +
           "ORDER BY m.createdAt DESC")
    List<ProductMapping> findByTenantIdAndMarketplaceProductIdAndMarketplaceSku(
        @Param("tenantId") UUID tenantId,
        @Param("productId") String productId,
        @Param("sku") String sku
    );

    /**
     * 테넌트 + 활성화 여부별 매핑 수 집계
     */
    long countByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    /**
     * 테넌트 + 스토어 + 마켓별 매핑 수 집계
     */
    long countByTenantIdAndStoreIdAndMarketplace(
        UUID tenantId,
        UUID storeId,
        Marketplace marketplace
    );

    /**
     * 매핑되지 않은 상품 확인
     * - OrderItem의 marketplace_product_id + marketplace_sku로 매핑이 없는 경우 조회
     */
    @Query("SELECT DISTINCT CONCAT(oi.marketplaceProductId, ':', oi.marketplaceSku) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.tenantId = :tenantId " +
           "AND oi.order.storeId = :storeId " +
           "AND oi.order.marketplace = :marketplace " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM ProductMapping pm " +
           "  WHERE pm.tenantId = :tenantId " +
           "  AND pm.storeId = :storeId " +
           "  AND pm.marketplace = :marketplace " +
           "  AND pm.marketplaceProductId = oi.marketplaceProductId " +
           "  AND pm.marketplaceSku = oi.marketplaceSku " +
           "  AND pm.isActive = TRUE" +
           ")")
    List<String> findUnmappedProducts(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("marketplace") Marketplace marketplace
    );

    /**
     * 매핑 상태별 조회 (스토어별 우선순위 정렬)
     */
    @Query("SELECT m FROM ProductMapping m WHERE m.tenantId = :tenantId " +
           "AND (m.storeId = :storeId OR m.storeId IS NULL) " +
           "AND m.marketplaceProductId = :productId " +
           "AND (m.marketplaceSku = :sku OR (m.marketplaceSku IS NULL AND :sku IS NULL)) " +
           "ORDER BY CASE WHEN m.storeId IS NOT NULL THEN 0 ELSE 1 END")
    List<ProductMapping> findMappings(@Param("tenantId") UUID tenantId,
                                       @Param("storeId") UUID storeId,
                                       @Param("productId") String productId,
                                       @Param("sku") String sku);

    default Optional<ProductMapping> findMapping(UUID tenantId, UUID storeId, 
                                                   String productId, String sku) {
        List<ProductMapping> mappings = findMappings(tenantId, storeId, productId, sku);
        return mappings.isEmpty() ? Optional.empty() : Optional.of(mappings.get(0));
    }

    /**
     * 매핑 상태별 조회 (페이징)
     */
    Page<ProductMapping> findByTenantIdAndMappingStatusOrderByCreatedAtDesc(
            UUID tenantId, MappingStatus status, Pageable pageable);

    /**
     * 키워드 검색 (상품명)
     */
    @Query("SELECT m FROM ProductMapping m WHERE m.tenantId = :tenantId " +
           "AND (LOWER(m.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ProductMapping> searchByKeyword(@Param("tenantId") UUID tenantId,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    /**
     * 매핑 상태별 수 집계
     */
    long countByTenantIdAndMappingStatus(UUID tenantId, MappingStatus status);
}
