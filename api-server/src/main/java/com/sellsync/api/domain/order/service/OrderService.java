package com.sellsync.api.domain.order.service;

import com.sellsync.api.domain.order.dto.OrderListResponse;
import com.sellsync.api.domain.order.dto.OrderResponse;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.settlement.entity.SettlementOrderItem;
import com.sellsync.api.domain.settlement.repository.SettlementOrderRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문(Order) 서비스
 * 
 * 멱등성 키: (store_id, marketplace_order_id)
 * - 동일 상점, 동일 마켓 주문번호는 1회만 저장
 * - 이미 존재하면 기존 주문 반환 또는 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final com.sellsync.api.domain.mapping.repository.ProductMappingRepository productMappingRepository;
    private final SettlementOrderRepository settlementOrderRepository;
    private final com.sellsync.api.domain.posting.repository.PostingRepository postingRepository;

    /**
     * 주문 저장/조회 (멱등 Upsert)
     * - 동일 멱등키로는 1회만 생성
     * - 이미 존재하면 기존 주문 업데이트 후 반환
     * 
     * 멱등키: store_id + marketplace_order_id
     */
    @Transactional
    public OrderResponse saveOrUpdate(Order order) {
        try {
            // 1. 멱등키로 기존 주문 조회
            return orderRepository.findByStoreIdAndMarketplaceOrderId(
                    order.getStoreId(),
                    order.getMarketplaceOrderId()
            )
            .map(existing -> {
                // 2. 기존 주문 업데이트
                updateExistingOrder(existing, order);
                Order updated = orderRepository.save(existing);
                log.info("[멱등성] 기존 주문 업데이트: orderId={}, marketplaceOrderId={}, status={}", 
                    updated.getOrderId(), updated.getMarketplaceOrderId(), updated.getOrderStatus());
                return OrderResponse.from(updated);
            })
            .orElseGet(() -> {
                // 3. 신규 주문 생성
                Order saved = orderRepository.save(order);
                log.info("[신규 생성] orderId={}, marketplace={}, marketplaceOrderId={}, status={}", 
                    saved.getOrderId(), saved.getMarketplace(), saved.getMarketplaceOrderId(), saved.getOrderStatus());
                return OrderResponse.from(saved);
            });
        } catch (DataIntegrityViolationException e) {
            // 4. 동시성: 중복 insert 발생 시 재조회 (멱등 수렴)
            log.warn("[동시성 처리] Unique 제약 위반 감지, 재조회 시도: storeId={}, marketplaceOrderId={}", 
                order.getStoreId(), order.getMarketplaceOrderId());
            
            Order existing = orderRepository.findByStoreIdAndMarketplaceOrderId(
                    order.getStoreId(),
                    order.getMarketplaceOrderId()
            )
            .orElseThrow(() -> new IllegalStateException("동시성 처리 중 주문 조회 실패"));
            
            // 동시성 경합 후 재조회된 주문도 업데이트
            updateExistingOrder(existing, order);
            Order updated = orderRepository.save(existing);
            return OrderResponse.from(updated);
        }
    }

    /**
     * 기존 주문 업데이트 (최신 데이터 반영)
     */
    private void updateExistingOrder(Order existing, Order newOrder) {
        // 주문 상태 업데이트
        existing.setOrderStatus(newOrder.getOrderStatus());
        
        // 수취인 정보 업데이트
        if (newOrder.getReceiverName() != null) {
            existing.setReceiverName(newOrder.getReceiverName());
            existing.setReceiverPhone1(newOrder.getReceiverPhone1());
            existing.setReceiverPhone2(newOrder.getReceiverPhone2());
            existing.setReceiverZipCode(newOrder.getReceiverZipCode());
            existing.setReceiverAddress(newOrder.getReceiverAddress());
        }
        
        // 금액 정보 업데이트
        existing.setTotalProductAmount(newOrder.getTotalProductAmount());
        existing.setTotalDiscountAmount(newOrder.getTotalDiscountAmount());
        existing.setTotalShippingAmount(newOrder.getTotalShippingAmount());
        existing.setTotalPaidAmount(newOrder.getTotalPaidAmount());
        
        // OrderItem은 기존 것을 유지 (중복 방지)
        // 실제 운영에서는 취소된 아이템 처리 등 추가 로직 필요
        
        log.debug("[주문 업데이트] orderId={}, status={} -> {}", 
            existing.getOrderId(), existing.getOrderStatus(), newOrder.getOrderStatus());
    }

    /**
     * 주문 조회 (ID)
     */
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        OrderResponse response = OrderResponse.from(order);
        
        // 정산 수집 완료된 경우, 정산 수수료 정보 추가
        if (order.getSettlementStatus() == SettlementCollectionStatus.COLLECTED 
            || order.getSettlementStatus() == SettlementCollectionStatus.POSTED) {
            
            // 정산 주문 조회
            List<SettlementOrder> settlementOrders = settlementOrderRepository.findByOrderId(orderId);
            log.debug("[OrderService.getById] orderId={}, settlementOrders.size={}", orderId, settlementOrders.size());
            
            if (!settlementOrders.isEmpty()) {
                SettlementOrder settlementOrder = settlementOrders.get(0);
                
                // items에서 상품별 수수료 집계
                BigDecimal productCommission = settlementOrder.getItems().stream()
                    .filter(item -> !"DELIVERY".equals(item.getProductOrderType()))
                    .map(SettlementOrderItem::calculateTotalCommission)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal shippingCommission = settlementOrder.getItems().stream()
                    .filter(item -> "DELIVERY".equals(item.getProductOrderType()))
                    .map(SettlementOrderItem::calculateTotalCommission)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                log.debug("[OrderService.getById] From SettlementOrder - productCommission={}, shippingCommission={}", 
                    productCommission, shippingCommission);
                
                response.setProductCommissionAmount(productCommission.longValue());
                
                // ⚠️ FIX: SettlementOrder에 DELIVERY 타입이 없으면 Order 엔티티의 값을 fallback으로 사용
                if (shippingCommission.compareTo(BigDecimal.ZERO) == 0 && order.getShippingCommissionAmount() != null && order.getShippingCommissionAmount() > 0) {
                    log.debug("[OrderService.getById] DELIVERY 타입 없음, Order 엔티티 fallback 사용 - shippingCommissionAmount={}", order.getShippingCommissionAmount());
                    response.setShippingCommissionAmount(order.getShippingCommissionAmount());
                } else {
                    response.setShippingCommissionAmount(shippingCommission.longValue());
                }
            } else {
                // SettlementOrder가 없는 경우, Order 엔티티의 수수료 필드를 fallback으로 사용
                // (정산 수집 시 bulkUpdateSettlementInfoByStoreId()에서 업데이트됨)
                log.info("[OrderService.getById] No SettlementOrder found, using Order entity fields as fallback. orderId={}", orderId);
                
                Long productCommission = order.getCommissionAmount() != null ? order.getCommissionAmount() : 0L;
                Long shippingCommission = order.getShippingCommissionAmount() != null ? order.getShippingCommissionAmount() : 0L;
                
                log.debug("[OrderService.getById] Fallback - productCommission={}, shippingCommission={}", 
                    productCommission, shippingCommission);
                
                response.setProductCommissionAmount(productCommission);
                response.setShippingCommissionAmount(shippingCommission);
            }
        }
        
        return response;
    }

    /**
     * 멱등키로 주문 조회
     */
    @Transactional(readOnly = true)
    public OrderResponse getByIdempotencyKey(UUID storeId, String marketplaceOrderId) {
        return orderRepository.findByStoreIdAndMarketplaceOrderId(storeId, marketplaceOrderId)
                .map(OrderResponse::from)
                .orElse(null);
    }

    /**
     * 마켓플레이스별 주문 목록 조회 (결재일 최근순)
     */
    @Transactional(readOnly = true)
    public Page<OrderListResponse> findByMarketplace(UUID tenantId, Marketplace marketplace, Pageable pageable) {
        return orderRepository.findByTenantIdAndMarketplaceOrderByPaidAtDesc(tenantId, marketplace, pageable)
                .map(OrderListResponse::from);
    }

    /**
     * 배치 저장 (멱등)
     * - 각 주문을 멱등 저장 처리
     */
    @Transactional
    public List<OrderResponse> saveOrUpdateBatch(List<Order> orders) {
        return orders.stream()
                .map(this::saveOrUpdate)
                .toList();
    }

    /**
     * 주문 목록 조회 (페이지네이션, 다양한 필터)
     * 
     * @param tenantId 테넌트 ID (필수)
     * @param status 주문 상태 (선택)
     * @param marketplaceStr 마켓플레이스 문자열 (선택)
     * @param storeId 스토어 ID (선택)
     * @param settlementStatus 정산 수집 상태 (선택)
     * @param search 검색 키워드 - 주문번호, 고객명, 상품명 (선택)
     * @param from 시작 날짜 (yyyy-MM-dd, 결제일 기준) (선택)
     * @param to 종료 날짜 (yyyy-MM-dd, 결제일 기준) (선택)
     * @param pageable 페이지 정보
     * @return 주문 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<OrderListResponse> getOrders(
            UUID tenantId,
            OrderStatus status,
            String marketplaceStr,
            UUID storeId,
            SettlementCollectionStatus settlementStatus,
            String search,
            String from,
            String to,
            Pageable pageable
    ) {
        // marketplace 문자열을 Enum으로 변환
        Marketplace marketplace = null;
        if (marketplaceStr != null && !marketplaceStr.isEmpty()) {
            try {
                marketplace = Marketplace.valueOf(marketplaceStr);
            } catch (IllegalArgumentException e) {
                log.warn("[주문 목록 조회] 잘못된 marketplace 값: {}", marketplaceStr);
            }
        }

        // 날짜 문자열을 LocalDate로 변환
        LocalDate fromDate = null;
        LocalDate toDate = null;
        if (from != null && !from.isEmpty()) {
            try {
                fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                log.warn("[주문 목록 조회] 잘못된 from 날짜 형식: {}", from);
            }
        }
        if (to != null && !to.isEmpty()) {
            try {
                toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                log.warn("[주문 목록 조회] 잘못된 to 날짜 형식: {}", to);
            }
        }

        // Specification을 사용한 동적 쿼리 생성
        Specification<Order> spec = createOrderSpecification(
            tenantId, status, marketplace, storeId, settlementStatus, search, fromDate, toDate
        );

        // 1단계: 페이지네이션으로 주문 조회 (items 미포함)
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        List<UUID> orderIds = orderPage.getContent().stream()
                .map(Order::getOrderId)
                .toList();

        log.debug("[주문 목록 조회] tenantId={}, status={}, marketplace={}, storeId={}, settlementStatus={}, search={}, from={}, to={}, page={}, size={}, total={}",
                tenantId, status, marketplace, storeId, settlementStatus, search, from, to,
                pageable.getPageNumber(), pageable.getPageSize(), orderPage.getTotalElements());

        if (orderIds.isEmpty()) {
            return orderPage.map(OrderListResponse::from);
        }

        // 2단계: items를 포함한 주문 배치 조회 (1회 DB 호출)
        List<Order> ordersWithItems = orderRepository.findByOrderIdInWithItems(orderIds);
        Map<UUID, Order> orderMap = ordersWithItems.stream()
                .collect(Collectors.toMap(Order::getOrderId, Function.identity()));

        // 3단계: 매핑 정보 배치 조회 (1회 DB 호출)
        Set<String> productSkuKeys = collectProductSkuKeys(ordersWithItems);
        Map<String, Boolean> mappingMap = batchLoadMappingStatus(tenantId, productSkuKeys);

        // 4단계: 전표 정보 배치 조회 (1회 DB 호출)
        Map<UUID, List<com.sellsync.api.domain.posting.entity.Posting>> postingMap = batchLoadPostings(tenantId, orderIds);

        // 5단계: 메모리에서 조합
        return orderPage.map(order -> {
            Order fullOrder = orderMap.getOrDefault(order.getOrderId(), order);
            OrderListResponse response = OrderListResponse.from(fullOrder);

            // 매핑 상태 계산 (메모리 매핑)
            response.setMappingStatus(calculateMappingStatusFromMap(fullOrder, mappingMap));

            // 정산 예정 금액 계산 (Order 엔티티 필드 사용, DB 호출 없음)
            response.setExpectedSettlementAmount(calculateSettlementFromOrder(fullOrder));

            // 전표 정보 설정 (메모리 매핑)
            setPostingInfoFromMap(fullOrder, response, postingMap);

            return response;
        });
    }

    /**
     * 주문 목록의 모든 아이템에서 productId:sku 키 수집
     */
    private Set<String> collectProductSkuKeys(List<Order> orders) {
        Set<String> keys = new HashSet<>();
        for (Order order : orders) {
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    String key = item.getMarketplaceProductId() + ":" +
                            (item.getMarketplaceSku() != null ? item.getMarketplaceSku() : "");
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /**
     * 매핑 상태 배치 조회 — productId:sku → 매핑 존재 여부 Map
     */
    private Map<String, Boolean> batchLoadMappingStatus(UUID tenantId, Set<String> productSkuKeys) {
        if (productSkuKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<com.sellsync.api.domain.mapping.entity.ProductMapping> mappings =
                productMappingRepository.findMappedByTenantIdAndProductSkuKeys(tenantId, new ArrayList<>(productSkuKeys));

        Map<String, Boolean> result = new HashMap<>();
        for (var mapping : mappings) {
            String key = mapping.getMarketplaceProductId() + ":" +
                    (mapping.getMarketplaceSku() != null ? mapping.getMarketplaceSku() : "");
            result.put(key, true);
        }
        return result;
    }

    /**
     * 전표 배치 조회 — orderId → Posting 목록 Map
     */
    private Map<UUID, List<com.sellsync.api.domain.posting.entity.Posting>> batchLoadPostings(UUID tenantId, List<UUID> orderIds) {
        List<com.sellsync.api.domain.posting.entity.Posting> postings =
                postingRepository.findByTenantIdAndOrderIdIn(tenantId, orderIds);
        return postings.stream()
                .collect(Collectors.groupingBy(com.sellsync.api.domain.posting.entity.Posting::getOrderId));
    }

    /**
     * 매핑 상태 계산 (메모리 Map 기반, DB 호출 없음)
     */
    private String calculateMappingStatusFromMap(Order order, Map<String, Boolean> mappingMap) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "UNMAPPED";
        }

        int totalItems = order.getItems().size();
        int mappedCount = 0;

        for (var item : order.getItems()) {
            String key = item.getMarketplaceProductId() + ":" +
                    (item.getMarketplaceSku() != null ? item.getMarketplaceSku() : "");
            if (Boolean.TRUE.equals(mappingMap.get(key))) {
                mappedCount++;
            }
        }

        if (mappedCount == 0) {
            return "UNMAPPED";
        } else if (mappedCount == totalItems) {
            return "MAPPED";
        } else {
            return "PARTIAL";
        }
    }

    /**
     * 정산 예정 금액 계산 (Order 엔티티 필드 사용, DB 호출 없음)
     */
    private Long calculateSettlementFromOrder(Order order) {
        if (order.getExpectedSettlementAmount() != null && order.getExpectedSettlementAmount() > 0) {
            return order.getExpectedSettlementAmount();
        }
        long paid = order.getTotalPaidAmount() != null ? order.getTotalPaidAmount() : 0L;
        long commission = order.getCommissionAmount() != null ? order.getCommissionAmount() : 0L;
        long result = paid - commission;
        return result > 0 ? result : 0L;
    }

    /**
     * 전표 정보 설정 (메모리 Map 기반, DB 호출 없음)
     */
    private void setPostingInfoFromMap(Order order, OrderListResponse response,
                                        Map<UUID, List<com.sellsync.api.domain.posting.entity.Posting>> postingMap) {
        List<com.sellsync.api.domain.posting.entity.Posting> postings =
                postingMap.getOrDefault(order.getOrderId(), Collections.emptyList());

        response.setHasPosting(!postings.isEmpty());

        if (!postings.isEmpty()) {
            String erpDocumentNo = postings.stream()
                    .filter(p -> p.getPostingStatus() == com.sellsync.api.domain.posting.enums.PostingStatus.POSTED)
                    .map(com.sellsync.api.domain.posting.entity.Posting::getErpDocumentNo)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            response.setErpDocumentNo(erpDocumentNo);
        }
    }
    
    /**
     * 전표 생성 가능 여부 확인
     * 
     * @param order 주문 엔티티
     * @return 모든 상품이 매핑되어 있으면 true, 아니면 false
     */
    public boolean isReadyForPosting(Order order) {
        String mappingStatus = calculateMappingStatus(order);
        return "MAPPED".equals(mappingStatus);
    }

    /**
     * 주문의 매핑 상태 계산
     * - MAPPED: 모든 상품 매핑됨
     * - UNMAPPED: 모든 상품 미매핑
     * - PARTIAL: 일부만 매핑됨
     */
    private String calculateMappingStatus(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return "UNMAPPED";
        }
        
        int totalItems = order.getItems().size();
        int mappedCount = 0;
        
        for (var item : order.getItems()) {
            // 각 OrderItem의 매핑 여부 확인
            boolean isMapped = productMappingRepository
                    .findByTenantIdAndMarketplaceProductIdAndMarketplaceSku(
                            order.getTenantId(),
                            item.getMarketplaceProductId(),
                            item.getMarketplaceSku()
                    )
                    .stream()
                    .anyMatch(mapping -> 
                        mapping.getMappingStatus() == com.sellsync.api.domain.mapping.enums.MappingStatus.MAPPED
                    );
            
            if (isMapped) {
                mappedCount++;
            }
        }
        
        if (mappedCount == 0) {
            return "UNMAPPED";
        } else if (mappedCount == totalItems) {
            return "MAPPED";
        } else {
            return "PARTIAL";
        }
    }

    /**
     * 상품 정산 예정 금액 계산
     * 
     * 정산 테이블에서 SALES 타입의 netPayoutAmount만 조회하여 반환
     * (SHIPPING_FEE 타입은 제외)
     * 
     * @param order 주문 엔티티
     * @return 상품 정산 예정 금액 (없으면 0)
     */
    private Long calculateProductSettlementAmount(Order order) {
        try {
            List<SettlementOrder> settlements = settlementOrderRepository
                    .findByOrderId(order.getOrderId());
            
            if (settlements.isEmpty()) {
                // 정산 데이터가 없으면 Order 엔티티의 expectedSettlementAmount 사용
                if (order.getExpectedSettlementAmount() != null && order.getExpectedSettlementAmount() > 0) {
                    return order.getExpectedSettlementAmount();
                }
                
                // expectedSettlementAmount도 없으면 주문 금액 - 수수료로 예상 정산 금액 계산
                Long totalPaidAmount = order.getTotalPaidAmount() != null ? order.getTotalPaidAmount() : 0L;
                Long commissionAmount = order.getCommissionAmount() != null ? order.getCommissionAmount() : 0L;
                Long estimatedSettlement = totalPaidAmount - commissionAmount;
                
                return estimatedSettlement > 0 ? estimatedSettlement : 0L;
            }
            
            // 여러 개가 있을 경우 합산 (일반적으로는 1개만 있어야 함)
            // 상품 타입(DELIVERY가 아닌 것)의 정산 금액만 합산
            BigDecimal total = settlements.stream()
                    .flatMap(so -> so.getItems().stream())
                    .filter(item -> !"DELIVERY".equals(item.getProductOrderType()))
                    .map(SettlementOrderItem::getSettleExpectAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return total.longValue();
        } catch (Exception e) {
            log.error("[상품 정산 금액 조회 실패] orderId={}", order.getOrderId(), e);
            return 0L;
        }
    }

    /**
     * 전표 정보 설정 (전표 존재 여부 + ERP 전표번호)
     *
     * - hasPosting: 전표가 하나라도 존재하면 true (READY 등 ERP 전송 전 상태 포함)
     * - erpDocumentNo: POSTED 상태인 전표의 ERP 전표번호
     */
    private void setPostingInfo(Order order, OrderListResponse response) {
        try {
            List<com.sellsync.api.domain.posting.entity.Posting> postings =
                    postingRepository.findByTenantIdAndOrderId(order.getTenantId(), order.getOrderId());

            response.setHasPosting(!postings.isEmpty());

            if (!postings.isEmpty()) {
                // POSTED 상태인 전표 중 첫 번째 전표의 erpDocumentNo 반환
                String erpDocumentNo = postings.stream()
                        .filter(posting -> posting.getPostingStatus() == com.sellsync.api.domain.posting.enums.PostingStatus.POSTED)
                        .map(com.sellsync.api.domain.posting.entity.Posting::getErpDocumentNo)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                response.setErpDocumentNo(erpDocumentNo);
            }
        } catch (Exception e) {
            log.error("[전표 정보 조회 실패] orderId={}", order.getOrderId(), e);
        }
    }

    /**
     * Order 검색을 위한 Specification 생성
     */
    private Specification<Order> createOrderSpecification(
            UUID tenantId, 
            OrderStatus status, 
            Marketplace marketplace, 
            UUID storeId,
            SettlementCollectionStatus settlementStatus,
            String search,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, cb) -> {
            // items는 별도 배치 조회(findByOrderIdInWithItems)로 로딩하므로
            // Specification에서는 fetch join하지 않음 (페이지네이션 호환성 유지)

            // WHERE 조건 생성
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            
            // tenantId는 필수
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            
            // status 필터 (optional)
            if (status != null) {
                predicates.add(cb.equal(root.get("orderStatus"), status));
            }
            
            // marketplace 필터 (optional)
            if (marketplace != null) {
                predicates.add(cb.equal(root.get("marketplace"), marketplace));
            }
            
            // storeId 필터 (optional)
            if (storeId != null) {
                predicates.add(cb.equal(root.get("storeId"), storeId));
            }
            
            // settlementStatus 필터 (optional)
            if (settlementStatus != null) {
                predicates.add(cb.equal(root.get("settlementStatus"), settlementStatus));
            }
            
            // 검색 키워드 필터 (주문번호, 고객명, 상품명)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                
                var searchPredicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                
                // 주문번호 검색 (marketplaceOrderId, bundleOrderId)
                searchPredicates.add(cb.like(cb.lower(root.get("marketplaceOrderId")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(root.get("bundleOrderId")), searchPattern));
                
                // 고객명 검색 (buyerName, receiverName)
                searchPredicates.add(cb.like(cb.lower(root.get("buyerName")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(root.get("receiverName")), searchPattern));
                
                // 상품명 검색 (OrderItem의 productName)
                // items join 시 중복 방지를 위해 distinct 설정
                var itemsRoot = root.join("items", JoinType.LEFT);
                searchPredicates.add(cb.like(cb.lower(itemsRoot.get("productName")), searchPattern));
                query.distinct(true);
                
                predicates.add(cb.or(searchPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            
            // 날짜 범위 필터 (결제일 기준)
            if (fromDate != null) {
                LocalDateTime fromDateTime = fromDate.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("paidAt"), fromDateTime));
            }
            if (toDate != null) {
                LocalDateTime toDateTime = toDate.plusDays(1).atStartOfDay(); // 종료일 포함
                predicates.add(cb.lessThan(root.get("paidAt"), toDateTime));
            }
            
            // 결재일 기준 최근순 정렬 추가
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(root.get("paidAt")));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
