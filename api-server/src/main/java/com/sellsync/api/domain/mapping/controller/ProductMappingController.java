package com.sellsync.api.domain.mapping.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.common.PageResponse;
import com.sellsync.api.domain.mapping.dto.ProductMappingRequest;
import com.sellsync.api.domain.mapping.dto.ProductMappingResponse;
import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.enums.MappingStatus;
import com.sellsync.api.domain.mapping.repository.ProductMappingRepository;
import com.sellsync.api.domain.mapping.service.ProductMappingService;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 상품-품목 매핑 컨트롤러
 */
@RestController
@RequestMapping("/api/mappings/products")
@RequiredArgsConstructor
public class ProductMappingController {

    private final ProductMappingService mappingService;
    private final ProductMappingRepository mappingRepository;

    /**
     * 매핑 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ProductMapping>>> getMappings(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ProductMapping> mappingPage;

        if (keyword != null && !keyword.isBlank()) {
            mappingPage = mappingRepository.searchByKeyword(user.getTenantId(), keyword, pageRequest);
        } else {
            MappingStatus mappingStatus = status != null ? 
                    MappingStatus.valueOf(status) : MappingStatus.UNMAPPED;
            mappingPage = mappingRepository.findByTenantIdAndMappingStatusOrderByCreatedAtDesc(
                    user.getTenantId(), mappingStatus, pageRequest);
        }

        PageResponse<ProductMapping> response = PageResponse.<ProductMapping>builder()
                .items(mappingPage.getContent())
                .page(mappingPage.getNumber())
                .size(mappingPage.getSize())
                .totalElements(mappingPage.getTotalElements())
                .totalPages(mappingPage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 매핑 생성 또는 조회 (멱등)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProductMappingResponse>> createOrGetMapping(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateMappingRequest request) {

        // 문자열을 UUID/Enum으로 변환
        UUID storeIdUuid = request.getStoreId() != null ? UUID.fromString(request.getStoreId()) : null;
        Marketplace marketplaceEnum = request.getMarketplace() != null ? 
            Marketplace.valueOf(request.getMarketplace()) : null;

        ProductMappingRequest mappingRequest = ProductMappingRequest.builder()
                .tenantId(user.getTenantId())
                .storeId(storeIdUuid)
                .marketplace(marketplaceEnum)
                .marketplaceProductId(request.getMarketplaceProductId())
                .marketplaceSku(request.getMarketplaceSku())
                .erpCode(request.getErpCode())
                .productName(request.getProductName())
                .optionName(request.getOptionName())
                .isActive(true)
                .build();

        ProductMappingResponse response = mappingService.createOrGet(mappingRequest);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 상품 ID로 매핑 찾기
     */
    @GetMapping("/find")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProductMapping>> findMapping(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String marketplaceProductId,
            @RequestParam(required = false) String marketplaceSku) {

        // 테넌트의 모든 매핑 중에서 marketplace_product_id와 sku로 찾기
        List<ProductMapping> mappings = mappingRepository.findByTenantIdAndMarketplaceProductIdAndMarketplaceSku(
                user.getTenantId(), marketplaceProductId, marketplaceSku);
        
        ProductMapping mapping = mappings.isEmpty() ? null : mappings.get(0);

        return ResponseEntity.ok(ApiResponse.ok(mapping));
    }

    /**
     * 미매핑 수 조회
     */
    @GetMapping("/unmapped/count")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnmappedCount(
            @AuthenticationPrincipal CustomUserDetails user) {

        long unmapped = mappingRepository.countByTenantIdAndMappingStatus(
                user.getTenantId(), MappingStatus.UNMAPPED);
        long suggested = mappingRepository.countByTenantIdAndMappingStatus(
                user.getTenantId(), MappingStatus.SUGGESTED);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "unmapped", unmapped,
                "suggested", suggested
        )));
    }

    /**
     * 매핑 통계 조회
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<MappingStats>> getStats(
            @AuthenticationPrincipal CustomUserDetails user) {

        long total = mappingRepository.countByTenantIdAndIsActive(user.getTenantId(), true);
        long unmapped = mappingRepository.countByTenantIdAndMappingStatus(
                user.getTenantId(), MappingStatus.UNMAPPED);
        long suggested = mappingRepository.countByTenantIdAndMappingStatus(
                user.getTenantId(), MappingStatus.SUGGESTED);
        long mapped = mappingRepository.countByTenantIdAndMappingStatus(
                user.getTenantId(), MappingStatus.MAPPED);

        double completionRate = total > 0 ? ((double) mapped / total) * 100 : 0.0;

        MappingStats stats = MappingStats.builder()
                .total(total)
                .unmapped(unmapped)
                .suggested(suggested)
                .mapped(mapped)
                .completionRate(completionRate)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    /**
     * 미매핑 상품 목록 조회
     */
    @GetMapping("/unmapped")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ProductMapping>>> getUnmappedList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ProductMapping> mappingPage = mappingRepository.findByTenantIdAndMappingStatusOrderByCreatedAtDesc(
                user.getTenantId(), MappingStatus.UNMAPPED, pageRequest);

        PageResponse<ProductMapping> response = PageResponse.<ProductMapping>builder()
                .items(mappingPage.getContent())
                .page(mappingPage.getNumber())
                .size(mappingPage.getSize())
                .totalElements(mappingPage.getTotalElements())
                .totalPages(mappingPage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 수동 매핑
     */
    @PostMapping("/{mappingId}/map")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProductMapping>> manualMap(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID mappingId,
            @Valid @RequestBody ManualMapRequest request) {

        ProductMapping mapping = mappingService.manualMap(
                mappingId, request.getErpItemCode(), user.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(mapping));
    }

    /**
     * 추천 확정
     */
    @PostMapping("/{mappingId}/confirm")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProductMapping>> confirmSuggestion(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID mappingId) {

        ProductMapping mapping = mappingService.confirmSuggestion(mappingId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(mapping));
    }

    /**
     * 매핑 해제
     */
    @PostMapping("/{mappingId}/unmap")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProductMapping>> unmap(@PathVariable UUID mappingId) {
        ProductMapping mapping = mappingService.unmap(mappingId);
        return ResponseEntity.ok(ApiResponse.ok(mapping));
    }

    /**
     * 일괄 매핑
     */
    @PostMapping("/bulk-map")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkMap(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BulkMapRequest request) {

        int success = 0, failed = 0;
        for (BulkMapItem item : request.getMappings()) {
            try {
                mappingService.manualMap(item.getMappingId(), item.getErpItemCode(), user.getUserId());
                success++;
            } catch (Exception e) {
                failed++;
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", success, "failed", failed)));
    }

    /**
     * 수동 매핑 요청 DTO
     */
    @Data
    public static class ManualMapRequest {
        @NotBlank(message = "ERP 품목코드는 필수입니다")
        private String erpItemCode;
    }

    /**
     * 일괄 매핑 요청 DTO
     */
    @Data
    public static class BulkMapRequest {
        private List<BulkMapItem> mappings;
    }

    /**
     * 일괄 매핑 항목 DTO
     */
    @Data
    public static class BulkMapItem {
        private UUID mappingId;
        private String erpItemCode;
    }

    /**
     * 매핑 통계 DTO
     */
    @Data
    @lombok.Builder
    public static class MappingStats {
        private long total;
        private long unmapped;
        private long suggested;
        private long mapped;
        private double completionRate;
    }

    /**
     * 매핑 생성 요청 DTO
     */
    @Data
    public static class CreateMappingRequest {
        private String storeId; // UUID 문자열
        private String marketplace; // Marketplace enum 문자열
        @NotBlank(message = "마켓플레이스 상품 ID는 필수입니다")
        private String marketplaceProductId;
        private String marketplaceSku;
        @NotBlank(message = "ERP 코드는 필수입니다")
        private String erpCode;
        private String productName;
        private String optionName;
    }
}
