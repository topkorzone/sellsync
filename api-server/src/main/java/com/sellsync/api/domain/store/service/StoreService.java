package com.sellsync.api.domain.store.service;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.store.dto.CreateStoreRequest;
import com.sellsync.api.domain.store.dto.StoreResponse;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 스토어 Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreService {

    private final StoreRepository storeRepository;

    /**
     * 스토어 목록 조회 (tenant 기준)
     */
    @Transactional(readOnly = true)
    public List<StoreResponse> getStoresByTenant(UUID tenantId, String marketplace) {
        List<Store> stores;
        
        if (marketplace != null && !marketplace.isEmpty()) {
            Marketplace marketplaceEnum = Marketplace.valueOf(marketplace);
            stores = storeRepository.findByTenantId(tenantId)
                    .stream()
                    .filter(s -> s.getMarketplace() == marketplaceEnum)
                    .collect(Collectors.toList());
        } else {
            stores = storeRepository.findByTenantId(tenantId);
        }

        return stores.stream()
                .map(StoreResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 스토어 상세 조회
     */
    @Transactional(readOnly = true)
    public StoreResponse getStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));
        return StoreResponse.from(store);
    }

    /**
     * 스토어 생성
     */
    @Transactional
    public StoreResponse createStore(CreateStoreRequest request) {
        log.info("[StoreService] Creating store: tenantId={}, marketplace={}, storeName={}", 
                request.getTenantId(), request.getMarketplace(), request.getStoreName());

        Store store = Store.builder()
                .tenantId(request.getTenantId())
                .storeName(request.getStoreName())
                .marketplace(request.getMarketplace())
                .isActive(true)
                .build();

        Store saved = storeRepository.save(store);
        log.info("[StoreService] Store created: storeId={}", saved.getStoreId());

        return StoreResponse.from(saved);
    }

    /**
     * 스토어 활성화/비활성화
     */
    @Transactional
    public StoreResponse updateStoreStatus(UUID storeId, Boolean isActive) {
        log.info("[StoreService] Updating store status: storeId={}, isActive={}", storeId, isActive);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        store.setIsActive(isActive);
        Store updated = storeRepository.save(store);

        log.info("[StoreService] Store status updated: storeId={}", storeId);
        return StoreResponse.from(updated);
    }

    /**
     * 스토어 ERP 거래처코드 설정
     */
    @Transactional
    public StoreResponse updateErpCustomerCode(UUID storeId, String erpCustomerCode) {
        log.info("[StoreService] Updating ERP customer code: storeId={}, erpCustomerCode={}", 
                storeId, erpCustomerCode);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        store.setErpCustomerCode(erpCustomerCode);
        Store updated = storeRepository.save(store);

        log.info("[StoreService] ERP customer code updated: storeId={}", storeId);
        return StoreResponse.from(updated);
    }

    /**
     * 스토어 삭제
     */
    @Transactional
    public void deleteStore(UUID storeId) {
        log.info("[StoreService] Deleting store: storeId={}", storeId);

        if (!storeRepository.existsById(storeId)) {
            throw new IllegalArgumentException("Store not found: " + storeId);
        }

        storeRepository.deleteById(storeId);
        log.info("[StoreService] Store deleted: storeId={}", storeId);
    }
}
