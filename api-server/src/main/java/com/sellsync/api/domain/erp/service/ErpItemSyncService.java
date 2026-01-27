package com.sellsync.api.domain.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.erp.client.ErpClient;
import com.sellsync.api.domain.erp.dto.ErpItemDto;
import com.sellsync.api.domain.erp.dto.ErpItemSearchRequest;
import com.sellsync.api.domain.erp.entity.ErpItem;
import com.sellsync.api.domain.erp.entity.ErpItemSyncHistory;
import com.sellsync.api.domain.erp.repository.ErpItemRepository;
import com.sellsync.api.domain.erp.repository.ErpItemSyncHistoryRepository;
import com.sellsync.infra.erp.ecount.EcountClient;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErpItemSyncService {

    private final ErpItemRepository erpItemRepository;
    private final ErpItemSyncHistoryRepository syncHistoryRepository;
    private final List<ErpClient> erpClients;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class SyncResult {
        private int totalFetched;
        private int created;
        private int updated;
        private int deactivated;
    }

    @Transactional(timeout = 300)  // 5분 타임아웃 (대용량 ERP 품목 동기화)
    public SyncResult syncItems(UUID tenantId, String erpCode, String triggerType) {
        log.info("[ErpItemSync] Starting sync for tenant {} ({})", tenantId, erpCode);
        
        LocalDateTime syncStartTime = LocalDateTime.now();
        ErpItemSyncHistory history = createSyncHistory(tenantId, erpCode, triggerType);

        try {
            ErpClient client = getClient(erpCode);
            List<ErpItemDto> allItems = fetchAllItems(tenantId, client);

            log.info("[ErpItemSync] Fetched {} items from ERP", allItems.size());

            // 재고 현황 조회 (Ecount인 경우에만)
            Map<String, EcountClient.InventoryBalance> inventoryBalances = new HashMap<>();
            if ("ECOUNT".equals(erpCode) && client instanceof EcountClient) {
                try {
                    EcountClient ecountClient = (EcountClient) client;
                    String baseDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    inventoryBalances = ecountClient.getInventoryBalances(tenantId, baseDate);
                    log.info("[ErpItemSync] Fetched {} inventory balances", inventoryBalances.size());
                } catch (Exception e) {
                    log.warn("[ErpItemSync] Failed to fetch inventory balances, continuing without stock data: {}", 
                            e.getMessage());
                }
            }

            // 배치 처리를 위한 리스트 - 새 아이템과 기존 아이템 분리
            List<ErpItem> newItems = new ArrayList<>();
            List<ErpItem> existingItems = new ArrayList<>();
            int created = 0, updated = 0;

            // 기존 품목 맵 생성 (성능 최적화)
            Map<String, ErpItem> existingItemsMap = buildExistingItemsMap(tenantId, erpCode);
            
            // 중복 제거 및 병합: 같은 item_code가 여러 번 나오면 마지막 것으로 덮어쓰기 (업데이트 처리)
            Map<String, ErpItemDto> uniqueItems = new LinkedHashMap<>();
            int duplicateCount = 0;
            for (ErpItemDto dto : allItems) {
                String itemCode = dto.getItemCode();
                if (uniqueItems.containsKey(itemCode)) {
                    duplicateCount++;
                    log.debug("[ErpItemSync] Duplicate item_code found, updating: {} (keeping last occurrence)", itemCode);
                }
                uniqueItems.put(itemCode, dto); // 마지막 데이터로 업데이트
            }
            
            if (duplicateCount > 0) {
                log.info("[ErpItemSync] Found {} duplicate items in ERP response, applied update (last occurrence)", duplicateCount);
            }

            for (ErpItemDto dto : uniqueItems.values()) {
                String itemCode = dto.getItemCode();
                ErpItem item;
                boolean isNew = false;

                if (existingItemsMap.containsKey(itemCode)) {
                    item = existingItemsMap.get(itemCode);
                    updated++;
                } else {
                    item = new ErpItem();
                    item.setErpItemId(UUID.randomUUID()); // ID 생성 필수!
                    item.setTenantId(tenantId);
                    item.setErpCode(erpCode);
                    item.setItemCode(itemCode);
                    isNew = true;
                    created++;
                }

                // 품목 정보 업데이트
                updateItemFromDto(item, dto, syncStartTime);
                
                // 재고 정보 맵핑
                if (inventoryBalances.containsKey(itemCode)) {
                    EcountClient.InventoryBalance balance = inventoryBalances.get(itemCode);
                    // 재고 수량을 Integer로 변환 (소수점 반올림)
                    item.setStockQty(balance.getBalanceQty() != null 
                            ? balance.getBalanceQty().intValue() 
                            : 0);
                    // 가용 수량도 동일하게 설정 (별도 API가 없으면 재고 수량과 동일)
                    item.setAvailableQty(balance.getBalanceQty() != null 
                            ? balance.getBalanceQty().intValue() 
                            : 0);
                    // 창고코드 저장
                    item.setWarehouseCode(balance.getWarehouseCode());
                    
                    log.debug("[ErpItemSync] Mapped inventory for {}: stock={}, warehouse={}", 
                            itemCode, balance.getBalanceQty(), balance.getWarehouseCode());
                }
                
                // 새 아이템과 기존 아이템 분리
                if (isNew) {
                    newItems.add(item);
                } else {
                    existingItems.add(item);
                }
            }

            // 배치로 저장 (BATCH_SIZE 단위로 처리)
            int batchSize = 100;
            
            // 새 아이템 저장
            if (!newItems.isEmpty()) {
                for (int i = 0; i < newItems.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, newItems.size());
                    List<ErpItem> batch = newItems.subList(i, end);
                    erpItemRepository.saveAll(batch);
                    erpItemRepository.flush();  // Hibernate 세션 플러시
                    log.debug("[ErpItemSync] Saved new items batch {}-{} of {}", i + 1, end, newItems.size());
                }
            }
            
            // 기존 아이템 저장 (업데이트)
            if (!existingItems.isEmpty()) {
                for (int i = 0; i < existingItems.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, existingItems.size());
                    List<ErpItem> batch = existingItems.subList(i, end);
                    erpItemRepository.saveAll(batch);
                    erpItemRepository.flush();  // Hibernate 세션 플러시
                    log.debug("[ErpItemSync] Updated existing items batch {}-{} of {}", i + 1, end, existingItems.size());
                }
            }

            log.info("[ErpItemSync] Saved {} items (created: {}, updated: {})", 
                    newItems.size() + existingItems.size(), created, updated);

            // 동기화되지 않은 품목 비활성화
            int deactivated = erpItemRepository.deactivateNotSyncedItems(
                    tenantId, erpCode, syncStartTime, LocalDateTime.now());

            SyncResult result = SyncResult.builder()
                    .totalFetched(allItems.size())
                    .created(created)
                    .updated(updated)
                    .deactivated(deactivated)
                    .build();

            completeSyncHistory(history, result, null);

            log.info("[ErpItemSync] Completed: fetched={}, created={}, updated={}, deactivated={}",
                    result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getDeactivated());

            return result;

        } catch (Exception e) {
            String errorDetail = String.format("Failed for tenant=%s, erpCode=%s, error=%s", 
                    tenantId, erpCode, e.getMessage());
            log.error("[ErpItemSync] {}", errorDetail, e);
            
            // 스택 트레이스 주요 부분 로깅
            if (e.getCause() != null) {
                log.error("[ErpItemSync] Caused by: {}", e.getCause().getMessage());
            }
            
            completeSyncHistory(history, null, errorDetail);
            throw new RuntimeException("ERP item sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * ERP에서 전체 품목 조회 (페이징 처리)
     * - 대량의 품목을 처리하기 위해 페이징 방식으로 조회
     * - 첫 페이지 실패 시 빈 body로 재시도 (전체 조회)
     * - 중복 데이터 감지로 무한 루프 방지
     */
    private List<ErpItemDto> fetchAllItems(UUID tenantId, ErpClient client) {
        log.info("[ErpItemSync] Fetching all items from ERP for tenant {}", tenantId);
        
        List<ErpItemDto> allItems = new ArrayList<>();
        Set<String> fetchedItemCodes = new HashSet<>();  // 중복 감지용
        int pageSize = 500;  // 페이지당 500개씩 조회
        int currentPage = 1;
        boolean hasMore = true;
        int consecutiveErrors = 0;
        int maxConsecutiveErrors = 3;  // 연속 3회 실패 시 중단
        int previousSize = 0;  // 이전 페이지 크기
        
        while (hasMore) {
            log.info("[ErpItemSync] Fetching page {} (size: {})", currentPage, pageSize);
            
            ErpItemSearchRequest request = ErpItemSearchRequest.builder()
                    .page(currentPage)
                    .size(pageSize)
                    .build();
            
            try {
                List<ErpItemDto> pageItems = client.getItems(tenantId, request);
                
                // 성공 시 에러 카운터 초기화
                consecutiveErrors = 0;
                
                if (pageItems.isEmpty()) {
                    log.info("[ErpItemSync] No more items found at page {}", currentPage);
                    hasMore = false;
                } else {
                    // 중복 데이터 감지 (Ecount API가 페이징을 무시하고 같은 데이터를 반환하는 경우)
                    int duplicateCount = 0;
                    int newItemCount = 0;
                    
                    for (ErpItemDto item : pageItems) {
                        String itemCode = item.getItemCode();
                        if (fetchedItemCodes.contains(itemCode)) {
                            duplicateCount++;
                        } else {
                            fetchedItemCodes.add(itemCode);
                            allItems.add(item);
                            newItemCount++;
                        }
                    }
                    
                    log.info("[ErpItemSync] Fetched page {}: {} items ({} new, {} duplicates, total unique: {})", 
                            currentPage, pageItems.size(), newItemCount, duplicateCount, allItems.size());
                    
                    // 모든 데이터가 중복이면 더 이상 새 데이터가 없다는 의미
                    if (newItemCount == 0) {
                        log.info("[ErpItemSync] All items in page {} are duplicates - API is ignoring pagination. " +
                                "Stopping here with {} unique items", currentPage, allItems.size());
                        hasMore = false;
                    }
                    // 가져온 개수가 pageSize보다 적으면 마지막 페이지
                    else if (pageItems.size() < pageSize) {
                        log.info("[ErpItemSync] Last page reached (items: {} < pageSize: {})", 
                                pageItems.size(), pageSize);
                        hasMore = false;
                    }
                    // 이전 페이지와 크기가 같고, 대부분이 중복이면 (80% 이상) 페이징이 제대로 동작하지 않는 것으로 판단
                    else if (currentPage > 1 && pageItems.size() == previousSize && 
                            duplicateCount > pageItems.size() * 0.8) {
                        log.warn("[ErpItemSync] Detected API pagination issue: page {} has same size ({}) as previous " +
                                "and {}% duplicates. Stopping pagination.", 
                                currentPage, pageItems.size(), (duplicateCount * 100 / pageItems.size()));
                        hasMore = false;
                    }
                    else {
                        previousSize = pageItems.size();
                        currentPage++;
                        
                        // 운영 환경 안정성을 위해 페이지 간 짧은 딜레이 추가 (rate limiting 회피)
                        if (hasMore) {
                            try {
                                Thread.sleep(300);  // 300ms 대기
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                log.warn("[ErpItemSync] Sleep interrupted between pages");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                consecutiveErrors++;
                log.error("[ErpItemSync] Failed to fetch page {} (attempt {}/{}): {}", 
                        currentPage, consecutiveErrors, maxConsecutiveErrors, e.getMessage());
                
                // 412 에러는 더 이상 페이지가 없다는 의미이므로 정상 종료
                boolean is412Error = e.getMessage() != null && 
                    (e.getMessage().contains("412") || e.getMessage().contains("Precondition"));
                
                if (is412Error && !allItems.isEmpty()) {
                    log.info("[ErpItemSync] Received 412 Precondition Failed - no more pages available. " +
                            "Successfully fetched {} unique items from {} pages", allItems.size(), currentPage - 1);
                    hasMore = false;
                    break;
                }
                
                // 첫 페이지 실패 시 빈 body로 전체 조회 시도
                if (currentPage == 1) {
                    log.warn("[ErpItemSync] First page failed, trying to fetch all items without paging...");
                    try {
                        // 빈 request로 전체 조회
                        ErpItemSearchRequest emptyRequest = ErpItemSearchRequest.builder().build();
                        List<ErpItemDto> allItemsAtOnce = client.getItems(tenantId, emptyRequest);
                        
                        if (!allItemsAtOnce.isEmpty()) {
                            log.info("[ErpItemSync] Successfully fetched {} items without paging", 
                                    allItemsAtOnce.size());
                            return allItemsAtOnce;
                        }
                    } catch (Exception retryError) {
                        log.error("[ErpItemSync] Failed to fetch all items without paging: {}", 
                                retryError.getMessage());
                    }
                    throw e; // 재시도도 실패하면 원래 예외를 던짐
                }
                
                // 연속 실패 횟수가 임계값을 넘으면 중단
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    log.error("[ErpItemSync] Too many consecutive errors ({}/{}), stopping sync", 
                            consecutiveErrors, maxConsecutiveErrors);
                    if (allItems.isEmpty()) {
                        throw e; // 아무것도 가져오지 못했으면 예외를 던짐
                    } else {
                        log.warn("[ErpItemSync] Returning {} unique items fetched so far", allItems.size());
                        hasMore = false;
                    }
                } else {
                    // 중간 페이지 실패 시 지금까지 가져온 데이터를 반환
                    log.warn("[ErpItemSync] Stopping at page {} due to error. Total unique items fetched: {}", 
                            currentPage, allItems.size());
                    hasMore = false;
                }
            }
        }
        
        log.info("[ErpItemSync] Completed fetching all items. Total unique: {}", allItems.size());
        return allItems;
    }

    /**
     * 기존 품목 맵 생성 (배치 처리 최적화)
     */
    private Map<String, ErpItem> buildExistingItemsMap(UUID tenantId, String erpCode) {
        List<ErpItem> existingItems = erpItemRepository.findByTenantIdAndErpCodeAndIsActive(
                tenantId, erpCode, true);
        
        // 비활성 품목도 포함 (재활성화를 위해)
        List<ErpItem> inactiveItems = erpItemRepository.findByTenantIdAndErpCodeAndIsActive(
                tenantId, erpCode, false);
        existingItems.addAll(inactiveItems);
        
        Map<String, ErpItem> map = new HashMap<>();
        for (ErpItem item : existingItems) {
            map.put(item.getItemCode(), item);
        }
        
        log.info("[ErpItemSync] Loaded {} existing items into map", map.size());
        return map;
    }

    /**
     * DTO에서 Entity로 정보 업데이트
     * - 재고 수량과 창고코드는 별도 API로 조회하므로 여기서는 설정하지 않음
     */
    private void updateItemFromDto(ErpItem item, ErpItemDto dto, LocalDateTime syncTime) {
        item.setItemName(dto.getItemName());
        item.setItemSpec(dto.getItemSpec());
        item.setUnit(dto.getUnit());
        item.setUnitPrice(dto.getUnitPrice());
        item.setItemType(dto.getItemType());
        item.setCategoryCode(dto.getCategoryCode());
        item.setCategoryName(dto.getCategoryName());
        // 재고 수량과 창고코드는 별도의 재고현황 API로 조회하여 설정
        // item.setStockQty(), item.setWarehouseCode()는 재고 맵핑 단계에서 처리
        item.setIsActive(dto.isActive());
        item.setLastSyncedAt(syncTime);

        try {
            item.setRawData(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            log.warn("Failed to serialize raw data for item {}", dto.getItemCode());
        }
    }

    @SuppressWarnings("null")
    private ErpItemSyncHistory createSyncHistory(UUID tenantId, String erpCode, String triggerType) {
        ErpItemSyncHistory history = ErpItemSyncHistory.builder()
                .tenantId(tenantId)
                .erpCode(erpCode)
                .triggerType(triggerType)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
        return syncHistoryRepository.save(history);
    }

    private void completeSyncHistory(ErpItemSyncHistory history, SyncResult result, String errorMessage) {
        history.setFinishedAt(LocalDateTime.now());
        
        if (result != null) {
            history.setStatus("SUCCESS");
            history.setTotalFetched(result.getTotalFetched());
            history.setCreatedCount(result.getCreated());
            history.setUpdatedCount(result.getUpdated());
            history.setDeactivatedCount(result.getDeactivated());
        } else {
            history.setStatus("FAILED");
            history.setErrorMessage(errorMessage);
        }

        syncHistoryRepository.save(history);
    }

    private ErpClient getClient(String erpCode) {
        return erpClients.stream()
                .filter(c -> c.getErpCode().equals(erpCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ERP: " + erpCode));
    }
}
