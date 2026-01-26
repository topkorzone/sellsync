package com.sellsync.api.domain.store.service;

import com.sellsync.api.domain.credential.repository.CredentialRepository;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.store.dto.CreateStoreRequest;
import com.sellsync.api.domain.store.dto.UpdateStoreRequest;
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
    private final CredentialRepository credentialRepository;

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
                // 수수료 품목 코드
                .commissionItemCode(request.getCommissionItemCode())
                .shippingCommissionItemCode(request.getShippingCommissionItemCode())
                // 기본 설정
                .defaultWarehouseCode(request.getDefaultWarehouseCode())
                .defaultCustomerCode(request.getDefaultCustomerCode())
                .shippingItemCode(request.getShippingItemCode())
                .build();

        Store saved = storeRepository.save(store);
        log.info("[StoreService] Store created: storeId={}", saved.getStoreId());

        return StoreResponse.from(saved);
    }

    /**
     * 스토어 수정
     */
    @Transactional
    public StoreResponse updateStore(UUID storeId, UpdateStoreRequest request) {
        log.info("[StoreService] Updating store: storeId={}", storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        // 선택적 필드 업데이트
        if (request.getStoreName() != null) {
            store.setStoreName(request.getStoreName());
        }
        if (request.getIsActive() != null) {
            store.setIsActive(request.getIsActive());
        }
        if (request.getCommissionItemCode() != null) {
            store.setCommissionItemCode(request.getCommissionItemCode());
        }
        if (request.getShippingCommissionItemCode() != null) {
            store.setShippingCommissionItemCode(request.getShippingCommissionItemCode());
        }
        if (request.getDefaultWarehouseCode() != null) {
            store.setDefaultWarehouseCode(request.getDefaultWarehouseCode());
        }
        if (request.getDefaultCustomerCode() != null) {
            store.setDefaultCustomerCode(request.getDefaultCustomerCode());
        }
        if (request.getShippingItemCode() != null) {
            store.setShippingItemCode(request.getShippingItemCode());
        }

        Store updated = storeRepository.save(store);
        log.info("[StoreService] Store updated: storeId={}", storeId);

        return StoreResponse.from(updated);
    }

    /**
     * 스토어 활성화/비활성화
     * @deprecated updateStore를 사용하세요
     */
    @Deprecated
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
     * @deprecated updateStore를 사용하세요
     */
    @Deprecated
    @Transactional
    public StoreResponse updateErpCustomerCode(UUID storeId, String erpCustomerCode) {
        log.info("[StoreService] Updating ERP customer code: storeId={}, erpCustomerCode={}", 
                storeId, erpCustomerCode);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        store.setDefaultCustomerCode(erpCustomerCode);
        // 하위 호환성을 위해 erpCustomerCode도 설정
        store.setErpCustomerCode(erpCustomerCode);
        Store updated = storeRepository.save(store);

        log.info("[StoreService] ERP customer code updated: storeId={}", storeId);
        return StoreResponse.from(updated);
    }

    /**
     * 스토어 수수료 품목 코드 설정
     * @deprecated updateStore를 사용하세요
     */
    @Deprecated
    @Transactional
    public StoreResponse updateCommissionItems(UUID storeId, String commissionItemCode, 
                                               String shippingCommissionItemCode, String shippingItemCode) {
        log.info("[StoreService] Updating commission items: storeId={}, commissionItemCode={}, shippingCommissionItemCode={}, shippingItemCode={}", 
                storeId, commissionItemCode, shippingCommissionItemCode, shippingItemCode);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        if (commissionItemCode != null) {
            store.setCommissionItemCode(commissionItemCode);
        }
        if (shippingCommissionItemCode != null) {
            store.setShippingCommissionItemCode(shippingCommissionItemCode);
        }
        if (shippingItemCode != null) {
            store.setShippingItemCode(shippingItemCode);
        }
        
        Store updated = storeRepository.save(store);

        log.info("[StoreService] Commission items updated: storeId={}", storeId);
        return StoreResponse.from(updated);
    }

    /**
     * 스토어 삭제
     * 
     * 주의: 외래 키 제약 조건을 만족하기 위해 연관된 데이터를 먼저 삭제합니다.
     * 삭제 순서:
     * 1. credentials (인증 정보)
     * 2. stores (스토어)
     */
    @Transactional
    public void deleteStore(UUID storeId) {
        log.info("[StoreService] Deleting store: storeId={}", storeId);

        if (!storeRepository.existsById(storeId)) {
            throw new IllegalArgumentException("Store not found: " + storeId);
        }

        // 1. 연관된 인증 정보 먼저 삭제
        try {
            credentialRepository.deleteByStoreId(storeId);
            log.info("[StoreService] Deleted credentials for store: storeId={}", storeId);
        } catch (Exception e) {
            log.warn("[StoreService] Failed to delete credentials (may not exist): storeId={}, error={}", 
                    storeId, e.getMessage());
        }

        // 2. 스토어 삭제
        storeRepository.deleteById(storeId);
        log.info("[StoreService] Store deleted: storeId={}", storeId);
    }
}
