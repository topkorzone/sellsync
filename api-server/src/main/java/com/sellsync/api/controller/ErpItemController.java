package com.sellsync.api.controller;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.common.PageResponse;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.entity.ErpItemSyncHistory;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.erp.repository.ErpItemSyncHistoryRepository;
import com.sellsync.api.domain.erp.service.ErpItemSyncService;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/erp/items")
@RequiredArgsConstructor
@Slf4j
public class ErpItemController {

    private final ErpItemRepository itemRepository;
    private final ErpItemSyncHistoryRepository syncHistoryRepository;
    private final ErpItemSyncService syncService;

    /**
     * 품목 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ErpItem>>> getItems(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ErpItem> itemPage;
        PageRequest pageRequest = PageRequest.of(page, size);

        if (keyword != null && !keyword.isBlank()) {
            itemPage = itemRepository.searchByKeyword(user.getTenantId(), keyword, pageRequest);
        } else {
            itemPage = itemRepository.findByTenantIdAndIsActiveOrderByItemNameAsc(
                    user.getTenantId(), true, pageRequest);
        }

        PageResponse<ErpItem> response = PageResponse.<ErpItem>builder()
                .items(itemPage.getContent())
                .page(itemPage.getNumber())
                .size(itemPage.getSize())
                .totalElements(itemPage.getTotalElements())
                .totalPages(itemPage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 품목 상세 조회
     */
    @SuppressWarnings("null")
    @GetMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ErpItem>> getItem(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID itemId) {

        ErpItem item = itemRepository.findById(itemId)
                .filter(i -> i.getTenantId().equals(user.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    /**
     * 수동 동기화 트리거
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerSync(
            @AuthenticationPrincipal CustomUserDetails user) {

        log.info("[ErpItem] Manual sync triggered by {}", user.getUserId());

        ErpItemSyncService.SyncResult result = syncService.syncItems(
                user.getTenantId(), "ECOUNT", "MANUAL");

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalFetched", result.getTotalFetched(),
                "created", result.getCreated(),
                "updated", result.getUpdated(),
                "deactivated", result.getDeactivated()
        )));
    }

    /**
     * 동기화 이력 조회
     */
    @GetMapping("/sync/history")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<ErpItemSyncHistory>>> getSyncHistory(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ErpItemSyncHistory> historyPage = syncHistoryRepository
                .findByTenantIdOrderByStartedAtDesc(user.getTenantId(), PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(historyPage.getContent()));
    }

    /**
     * 품목 수 조회
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getItemCount(
            @AuthenticationPrincipal CustomUserDetails user) {

        long activeCount = itemRepository.countByTenantIdAndIsActive(user.getTenantId(), true);
        long inactiveCount = itemRepository.countByTenantIdAndIsActive(user.getTenantId(), false);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "active", activeCount,
                "inactive", inactiveCount,
                "total", activeCount + inactiveCount
        )));
    }
}
