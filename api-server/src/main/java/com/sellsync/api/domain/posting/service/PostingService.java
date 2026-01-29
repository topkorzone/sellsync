package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.CancelPostingRequestDto;
import com.sellsync.api.domain.posting.dto.CreatePostingRequest;
import com.sellsync.api.domain.posting.dto.CreatePostingRequestDto;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import com.sellsync.api.domain.posting.entity.Posting;
import com.sellsync.api.domain.posting.entity.PostingAttempt;
import com.sellsync.api.domain.posting.enums.PostingStatus;
import com.sellsync.api.domain.posting.enums.PostingType;
import com.sellsync.api.domain.posting.exception.InvalidStateTransitionException;
import com.sellsync.api.domain.posting.exception.PostingNotFoundException;
import com.sellsync.api.domain.posting.repository.PostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 전표(Posting) 서비스 - ADR-0001 멱등성 & 상태머신 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    private final PostingRepository postingRepository;
    private final OrderRepository orderRepository;
    private final TemplateBasedPostingBuilder templateBasedPostingBuilder;
    private final com.sellsync.api.domain.store.repository.StoreRepository storeRepository;
    private final com.sellsync.api.domain.mapping.service.ProductMappingService productMappingService;

    /**
     * 전표 생성/조회 (멱등 Upsert)
     * - 동일 멱등키로는 1회만 생성
     * - 이미 존재하면 기존 전표 반환
     * 
     * ADR-0001: 멱등키 = tenant_id + erp_code + marketplace + order_id + posting_type
     */
    @Transactional
    public PostingResponse createOrGet(CreatePostingRequest request) {
        try {
            // 1. 멱등키로 기존 전표 조회
            return postingRepository.findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
                    request.getTenantId(),
                    request.getErpCode(),
                    request.getMarketplace(),
                    request.getMarketplaceOrderId(),
                    request.getPostingType()
            )
            .map(existing -> {
                log.info("[멱등성] 기존 전표 반환: postingId={}, status={}", 
                    existing.getPostingId(), existing.getPostingStatus());
                return PostingResponse.from(existing);
            })
            .orElseGet(() -> {
                // 2. 신규 전표 생성
                Posting newPosting = Posting.builder()
                        .tenantId(request.getTenantId())
                        .erpCode(request.getErpCode())
                        .orderId(request.getOrderId())
                        .marketplace(request.getMarketplace())
                        .marketplaceOrderId(request.getMarketplaceOrderId())
                        .postingType(request.getPostingType())
                        .postingStatus(PostingStatus.READY)
                        .originalPostingId(request.getOriginalPostingId())
                        .requestPayload(request.getRequestPayload())
                        .build();

                Posting saved = postingRepository.save(newPosting);
                log.info("[신규 생성] postingId={}, erpCode={}, marketplace={}, orderId={}, type={}", 
                    saved.getPostingId(), saved.getErpCode(), saved.getMarketplace(), 
                    saved.getMarketplaceOrderId(), saved.getPostingType());
                
                return PostingResponse.from(saved);
            });
        } catch (DataIntegrityViolationException e) {
            // 3. 동시성: 중복 insert 발생 시 재조회 (멱등 수렴)
            log.warn("[동시성 처리] Unique 제약 위반 감지, 재조회 시도: tenantId={}, erpCode={}, marketplace={}, orderId={}, type={}", 
                request.getTenantId(), request.getErpCode(), request.getMarketplace(), 
                request.getMarketplaceOrderId(), request.getPostingType());
            
            return postingRepository.findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
                    request.getTenantId(),
                    request.getErpCode(),
                    request.getMarketplace(),
                    request.getMarketplaceOrderId(),
                    request.getPostingType()
            )
            .map(PostingResponse::from)
            .orElseThrow(() -> new IllegalStateException("동시성 처리 중 전표 조회 실패"));
        }
    }

    /**
     * 전표 상태 전이 (ADR-0001 State Machine Guard)
     * - 허용되지 않은 전이는 예외 발생
     */
    @Transactional
    public PostingResponse transitionTo(UUID postingId, PostingStatus targetStatus) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        PostingStatus currentStatus = posting.getPostingStatus();

        // 상태 전이 가드 검증
        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.error("[상태 전이 금지] postingId={}, from={}, to={}", 
                postingId, currentStatus, targetStatus);
            throw new InvalidStateTransitionException(currentStatus, targetStatus);
        }

        // 상태 전이 실행
        posting.transitionTo(targetStatus);
        Posting updated = postingRepository.save(posting);

        log.info("[상태 전이 성공] postingId={}, from={} -> to={}", 
            postingId, currentStatus, targetStatus);

        return PostingResponse.from(updated);
    }

    /**
     * 전표 전송 성공 처리
     */
    @Transactional
    public PostingResponse markAsPosted(UUID postingId, String erpDocumentNo, String responsePayload) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        posting.markAsPosted(erpDocumentNo, responsePayload);
        Posting updated = postingRepository.save(posting);

        log.info("[전송 성공] postingId={}, erpDocNo={}", postingId, erpDocumentNo);

        return PostingResponse.from(updated);
    }

    /**
     * 전표 전송 실패 처리
     */
    @Transactional
    public PostingResponse markAsFailed(UUID postingId, String errorMessage) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        posting.markAsFailed(errorMessage);
        Posting updated = postingRepository.save(posting);

        log.error("[전송 실패] postingId={}, error={}", postingId, errorMessage);

        return PostingResponse.from(updated);
    }

    /**
     * 전표 재처리 (FAILED -> POSTING_REQUESTED)
     * - 실패한 전표를 재시도 가능 상태로 전이
     */
    @Transactional
    public PostingResponse reprocess(UUID postingId) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        if (!posting.isRetryable()) {
            throw new IllegalStateException(
                String.format("재처리 불가능한 상태: postingId=%s, status=%s", 
                    postingId, posting.getPostingStatus())
            );
        }

        // FAILED -> POSTING_REQUESTED (retry 전이)
        posting.transitionTo(PostingStatus.POSTING_REQUESTED);
        Posting updated = postingRepository.save(posting);

        log.info("[재처리 시작] postingId={}, status={}", postingId, updated.getPostingStatus());

        return PostingResponse.from(updated);
    }

    /**
     * 전표 시도 이력 추가
     */
    @Transactional
    public void addAttempt(UUID postingId, int attemptNumber, String status, 
                           String requestPayload, String responsePayload, 
                           String errorCode, String errorMessage) {
        addAttempt(postingId, attemptNumber, status, requestPayload, responsePayload, 
                   errorCode, errorMessage, null, null, null);
    }

    /**
     * 전표 시도 이력 추가 (전체 필드)
     */
    @Transactional
    public void addAttempt(UUID postingId, int attemptNumber, String status, 
                           String requestPayload, String responsePayload, 
                           String errorCode, String errorMessage,
                           String traceId, UUID jobId, Long executionTimeMs) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));

        PostingAttempt attempt = PostingAttempt.builder()
                .attemptNumber(attemptNumber)
                .status(status)
                .requestPayload(requestPayload)
                .responsePayload(responsePayload)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .traceId(traceId)
                .jobId(jobId)
                .executionTimeMs(executionTimeMs)
                .build();

        posting.addAttempt(attempt);
        postingRepository.save(posting);

        log.debug("[시도 이력 추가] postingId={}, attemptNumber={}, status={}, traceId={}, executionTime={}ms", 
            postingId, attemptNumber, status, traceId, executionTimeMs);
    }

    /**
     * 전표 조회 (ID)
     */
    @Transactional(readOnly = true)
    public PostingResponse getById(UUID postingId) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));
        
        // Order를 조회해서 금액 계산
        PostingResponse response = PostingResponse.from(posting);
        
        try {
            Order order = orderRepository.findById(posting.getOrderId()).orElse(null);
            
            if (order != null) {
                // bundleOrderId 설정
                if (order.getBundleOrderId() != null && !order.getBundleOrderId().isEmpty()) {
                    response.setBundleOrderId(order.getBundleOrderId());
                }
                
                // 첫 번째 아이템에서 상품주문 ID 설정
                if (order.getItems() != null && !order.getItems().isEmpty()) {
                    String productId = order.getItems().get(0).getMarketplaceProductId();
                    response.setMarketplaceProductId(productId);
                }
                
                // requestPayload에서 금액 추출 시도
                Long amount = extractAmountFromRequestPayload(posting.getRequestPayload(), posting.getPostingType());
                
                if (amount != null && amount > 0) {
                    log.info("[requestPayload에서 금액 추출 성공] postingId={}, type={}, amount={}", 
                            postingId, posting.getPostingType(), amount);
                    response.setTotalAmount(amount);
                } else {
                    // requestPayload에 금액이 없으면 Order에서 계산
                    Long productAmount = order.getTotalProductAmount();
                    if (productAmount == null || productAmount == 0) {
                        productAmount = order.getItems().stream()
                                .mapToLong(item -> item.getLineAmount() != null ? item.getLineAmount() : 0L)
                                .sum();
                    }
                    
                    Long shippingAmount = order.getTotalShippingAmount() != null ? 
                            order.getTotalShippingAmount() : 0L;
                    
                    amount = switch (posting.getPostingType()) {
                        case PRODUCT_SALES -> productAmount;
                        case SHIPPING_FEE -> shippingAmount;
                        case PRODUCT_CANCEL -> productAmount;
                        case SHIPPING_FEE_CANCEL -> shippingAmount;
                        default -> 0L;
                    };
                    
                    log.info("[Order에서 금액 계산] postingId={}, amount={}", postingId, amount);
                    response.setTotalAmount(amount);
                }
            }
        } catch (Exception e) {
            log.error("[전표 금액 계산 실패] postingId={}, error={}", postingId, e.getMessage(), e);
        }
        
        return response;
    }

    /**
     * 멱등키로 전표 조회
     */
    @Transactional(readOnly = true)
    public PostingResponse getByIdempotencyKey(UUID tenantId, String erpCode, 
                                                Marketplace marketplace, String marketplaceOrderId, 
                                                PostingType postingType) {
        return postingRepository.findByTenantIdAndErpCodeAndMarketplaceAndMarketplaceOrderIdAndPostingType(
                tenantId, erpCode, marketplace, marketplaceOrderId, postingType
        )
        .map(PostingResponse::from)
        .orElse(null);
    }

    /**
     * 전표 목록 조회 (페이지네이션, 다양한 필터)
     * 
     * @param tenantId 테넌트 ID (필수)
     * @param orderId 주문 ID (선택)
     * @param status 전표 상태 (선택)
     * @param postingType 전표 유형 (선택)
     * @param pageable 페이지 정보
     * @return 전표 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostingResponse> getPostings(
            UUID tenantId,
            UUID orderId,
            PostingStatus status,
            PostingType postingType,
            Pageable pageable
    ) {
        Page<Posting> postings;

        // 1. 주문 + 상태 필터 (우선순위 높음)
        if (orderId != null) {
            postings = postingRepository.findByTenantIdAndOrderIdOrderByUpdatedAtDesc(
                    tenantId, orderId, pageable
            );
        }
        // 2. 상태 필터만
        else if (status != null) {
            postings = postingRepository.findByTenantIdAndPostingStatusOrderByUpdatedAtDesc(
                    tenantId, status, pageable
            );
        }
        // 3. 전표 유형 필터만
        else if (postingType != null) {
            postings = postingRepository.findByTenantIdAndPostingTypeOrderByUpdatedAtDesc(
                    tenantId, postingType, pageable
            );
        }
        // 4. 기본 (테넌트만)
        else {
            postings = postingRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable);
        }

        log.debug("[전표 목록 조회] tenantId={}, orderId={}, status={}, type={}, total={}", 
                tenantId, orderId, status, postingType, postings.getTotalElements());

        // Order ID 목록 추출 (null 제외, 중복 제거)
        List<UUID> orderIds = postings.getContent().stream()
                .map(Posting::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Order 일괄 조회 (N+1 방지)
        Map<UUID, Order> orderMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<Order> orders = orderRepository.findAllById(orderIds);
            orderMap = orders.stream()
                    .collect(Collectors.toMap(
                            Order::getOrderId,
                            order -> order,
                            (a, b) -> a  // 중복 시 첫 번째 값 사용
                    ));
        }

        // PostingResponse 변환 및 bundleOrderId, marketplaceProductId, buyerName 설정
        final Map<UUID, Order> finalOrderMap = orderMap;
        return postings.map(posting -> {
            PostingResponse response = PostingResponse.from(posting);
            
            // Order 정보 설정
            if (posting.getOrderId() != null) {
                Order order = finalOrderMap.get(posting.getOrderId());
                
                if (order != null) {
                    // bundleOrderId 설정
                    if (order.getBundleOrderId() != null && !order.getBundleOrderId().isEmpty()) {
                        response.setBundleOrderId(order.getBundleOrderId());
                    }
                    
                    // marketplaceProductId 설정 (첫 번째 아이템에서)
                    if (order.getItems() != null && !order.getItems().isEmpty()) {
                        String productId = order.getItems().get(0).getMarketplaceProductId();
                        response.setMarketplaceProductId(productId);
                    }
                    
                    // buyerName 설정
                    if (order.getBuyerName() != null && !order.getBuyerName().isEmpty()) {
                        response.setBuyerName(order.getBuyerName());
                    }
                }
            }
            
            return response;
        });
    }

    /**
     * 주문 기반 전표 생성
     * 
     * @param orderId 주문 ID
     * @param request 전표 생성 요청 (mode, types)
     * @return 생성된 전표 목록
     */
    @Transactional
    public List<PostingResponse> createPostingsForOrder(UUID orderId, CreatePostingRequestDto request) {
        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 2. 상품 매핑 완료 확인 (필수)
        List<String> unmappedItems = checkProductMappings(order);
        if (!unmappedItems.isEmpty()) {
            String errorMsg = String.format(
                "상품 매핑이 완료되지 않은 항목이 있습니다. 매핑 관리 화면에서 먼저 매핑을 완료해주세요. orderId=%s, unmapped items=%s",
                orderId, unmappedItems
            );
            log.error("[전표 생성 차단 - 상품매핑 미완료] orderId={}, unmappedItems={}", orderId, unmappedItems);
            throw new IllegalStateException(errorMsg);
        }

        // 3. 일반 전표 생성 (정산 라우팅은 PostingFacadeService에서 처리)
        List<PostingResponse> createdPostings = new ArrayList<>();
        List<PostingType> typesToCreate = determinePostingTypes(request, order);

        for (PostingType type : typesToCreate) {
            CreatePostingRequest postingRequest = CreatePostingRequest.builder()
                    .tenantId(order.getTenantId())
                    .erpCode("ECOUNT") // TODO: Store에서 가져오기
                    .orderId(order.getOrderId())
                    .marketplace(order.getMarketplace())
                    .marketplaceOrderId(order.getMarketplaceOrderId())
                    .postingType(type)
                    .requestPayload(buildRequestPayload(order, type))
                    .build();

            PostingResponse posting = createOrGet(postingRequest);
            createdPostings.add(posting);
        }

        log.info("[주문 전표 생성] orderId={}, mode={}, types={}, created={}", 
                orderId, request.getMode(), typesToCreate, createdPostings.size());

        return createdPostings;
    }

    /**
     * 취소 전표 생성
     * 
     * @param orderId 주문 ID
     * @param request 취소 전표 생성 요청
     * @return 생성된 취소 전표
     */
    @Transactional
    public List<PostingResponse> createCancelPosting(UUID orderId, CancelPostingRequestDto request) {
        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<PostingResponse> cancelPostings = new ArrayList<>();

        // 2. 상품 취소 전표 생성
        BigDecimal canceledAmount = calculateCanceledAmount(request, order);
        
        CreatePostingRequest productCancelRequest = CreatePostingRequest.builder()
                .tenantId(order.getTenantId())
                .erpCode("ECOUNT") // TODO: Store에서 가져오기
                .orderId(order.getOrderId())
                .marketplace(order.getMarketplace())
                .marketplaceOrderId(order.getMarketplaceOrderId())
                .postingType(PostingType.PRODUCT_CANCEL)
                .requestPayload(buildCancelRequestPayload(order, request, canceledAmount))
                .build();

        PostingResponse productCancel = createOrGet(productCancelRequest);
        cancelPostings.add(productCancel);

        // 3. 배송비 취소 전표 생성 (필요 시)
        if (request.getRefundShipping() != null && request.getRefundShipping()) {
            CreatePostingRequest shippingCancelRequest = CreatePostingRequest.builder()
                    .tenantId(order.getTenantId())
                    .erpCode("ECOUNT")
                    .orderId(order.getOrderId())
                    .marketplace(order.getMarketplace())
                    .marketplaceOrderId(order.getMarketplaceOrderId())
                    .postingType(PostingType.SHIPPING_FEE_CANCEL)
                    .requestPayload(buildShippingCancelPayload(order))
                    .build();

            PostingResponse shippingCancel = createOrGet(shippingCancelRequest);
            cancelPostings.add(shippingCancel);
        }

        log.info("[취소 전표 생성] orderId={}, cancelType={}, amount={}, refundShipping={}, created={}", 
                orderId, request.getCancelType(), canceledAmount, request.getRefundShipping(), cancelPostings.size());

        return cancelPostings;
    }

    // ========== Private Helper Methods ==========

    /**
     * 생성할 전표 유형 결정
     */
    private List<PostingType> determinePostingTypes(CreatePostingRequestDto request, Order order) {
        if (request.getMode() == CreatePostingRequestDto.PostingMode.MANUAL) {
            return request.getTypes() != null ? request.getTypes() : List.of();
        }

        // AUTO 모드: 주문 정보 기반 자동 결정
        List<PostingType> types = new ArrayList<>();
        types.add(PostingType.PRODUCT_SALES);

        // 배송비가 있으면 배송비 전표 추가
        if (order.getTotalShippingAmount() != null && 
            order.getTotalShippingAmount() > 0) {
            types.add(PostingType.SHIPPING_FEE);
        }

        return types;
    }

    /**
     * 전표 요청 페이로드 생성 (템플릿 기반)
     */
    private String buildRequestPayload(Order order, PostingType type) {
        String erpCode = "ECOUNT"; // TODO: Store에서 가져오기
        
        try {
            // 템플릿 기반 전표 데이터 생성
            String payload = templateBasedPostingBuilder.buildPostingJson(order, erpCode, type);
            log.info("[템플릿 기반 페이로드 생성] orderId={}, type={}", order.getOrderId(), type);
            return payload;
        } catch (Exception e) {
            // 템플릿이 없거나 오류 발생 시 기본 방식 사용
            log.warn("[템플릿 기반 생성 실패 - 기본 방식 사용] orderId={}, type={}, error={}", 
                order.getOrderId(), type, e.getMessage());
            return buildFallbackPayload(order, type);
        }
    }
    
    /**
     * 기본 방식 전표 페이로드 생성 (거래처 & 창고 코드 포함)
     */
    private String buildFallbackPayload(Order order, PostingType type) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            
            // 기본 정보
            payload.put("orderId", order.getOrderId().toString());
            payload.put("type", type.name());
            
            // 금액
            BigDecimal amount = getAmountForType(order, type);
            payload.put("amount", amount);
            
            // 거래처 코드 (Store에서 가져오기)
            if (order.getStoreId() != null) {
                storeRepository.findById(order.getStoreId()).ifPresent(store -> {
                    if (store.getErpCustomerCode() != null && !store.getErpCustomerCode().isEmpty()) {
                        payload.put("CUST", store.getErpCustomerCode());
                        log.info("[기본 방식 - 거래처 코드 추가] storeId={}, erpCustomerCode={}", 
                            store.getStoreId(), store.getErpCustomerCode());
                    } else {
                        log.warn("[기본 방식 - 거래처 코드 없음] storeId={}, storeName={}", 
                            store.getStoreId(), store.getStoreName());
                    }
                });
            }
            
            // 창고 코드 (ProductMapping에서 가져오기)
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                var firstItem = order.getItems().get(0);
                productMappingService.findActiveMapping(
                    order.getTenantId(),
                    order.getStoreId(),
                    order.getMarketplace(),
                    firstItem.getMarketplaceProductId(),
                    firstItem.getMarketplaceSku()
                ).ifPresent(mapping -> {
                    if (mapping.getWarehouseCode() != null && !mapping.getWarehouseCode().isEmpty()) {
                        payload.put("WH_CD", mapping.getWarehouseCode());
                        log.info("[기본 방식 - 창고 코드 추가] productId={}, warehouseCode={}", 
                            mapping.getMarketplaceProductId(), mapping.getWarehouseCode());
                    } else {
                        log.warn("[기본 방식 - 창고 코드 없음] productId={}, erpItemCode={}", 
                            mapping.getMarketplaceProductId(), mapping.getErpItemCode());
                    }
                    
                    // ERP 품목코드도 추가
                    if (mapping.getErpItemCode() != null) {
                        payload.put("PROD_CD", mapping.getErpItemCode());
                    }
                });
            }
            
            // 주문일자
            if (order.getOrderedAt() != null) {
                payload.put("IO_DATE", order.getOrderedAt().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                ));
            }
            
            // 수량
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                int totalQty = order.getItems().stream()
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                    .sum();
                payload.put("QTY", totalQty);
            }
            
            String json = mapper.writeValueAsString(payload);
            log.info("[기본 방식 페이로드 생성 완료] orderId={}, type={}, fields={}", 
                order.getOrderId(), type, payload.keySet());
            return json;
            
        } catch (Exception ex) {
            log.error("[기본 방식 페이로드 생성 실패] orderId={}, type={}, error={}", 
                order.getOrderId(), type, ex.getMessage(), ex);
            // 최소한의 정보만 포함
            return String.format("{\"orderId\":\"%s\",\"type\":\"%s\",\"amount\":%s}", 
                order.getOrderId(), type, getAmountForType(order, type));
        }
    }

    /**
     * 취소 전표 요청 페이로드 생성
     */
    private String buildCancelRequestPayload(Order order, CancelPostingRequestDto request, BigDecimal amount) {
        // TODO: 실제 ERP API 요청 페이로드 생성 로직 구현
        return String.format("{\"orderId\":\"%s\",\"cancelType\":\"%s\",\"amount\":%s,\"reason\":\"%s\"}", 
                order.getOrderId(), request.getCancelType(), amount, request.getReason());
    }

    /**
     * 배송비 취소 페이로드 생성
     */
    private String buildShippingCancelPayload(Order order) {
        return String.format("{\"orderId\":\"%s\",\"shippingFee\":%s}", 
                order.getOrderId(), order.getTotalShippingAmount());
    }

    /**
     * 전표 유형별 금액 계산
     */
    private BigDecimal getAmountForType(Order order, PostingType type) {
        return switch (type) {
            case PRODUCT_SALES -> BigDecimal.valueOf(order.getTotalProductAmount());
            case SHIPPING_FEE -> BigDecimal.valueOf(order.getTotalShippingAmount());
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * 취소 금액 계산
     */
    private BigDecimal calculateCanceledAmount(CancelPostingRequestDto request, Order order) {
        if (request.getCancelType() == CancelPostingRequestDto.CancelType.FULL) {
            return BigDecimal.valueOf(order.getTotalPaidAmount());
        }

        // PARTIAL: canceledItems에서 합산
        if (request.getCanceledItems() != null) {
            return request.getCanceledItems().stream()
                    .map(CancelPostingRequestDto.CanceledItem::getCanceledAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 전표 상태별 통계 조회
     * 
     * @param tenantId 테넌트 ID
     * @return 상태별 전표 수
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatsByStatus(UUID tenantId) {
        Map<String, Long> stats = new java.util.HashMap<>();
        
        for (PostingStatus status : PostingStatus.values()) {
            long count = postingRepository.countByTenantIdAndPostingStatus(tenantId, status);
            stats.put(status.name(), count);
        }
        
        log.debug("[전표 통계] tenantId={}, stats={}", tenantId, stats);
        return stats;
    }
    
    /**
     * 전표 삭제
     * 
     * 규칙:
     * - POSTED 상태 전표는 삭제 불가 (이미 ERP 전송됨)
     * - POSTING_REQUESTED 상태도 삭제 불가 (전송 중)
     * - READY, FAILED 상태만 삭제 가능
     * 
     * @param postingId 전표 ID
     */
    @Transactional
    public void deletePosting(UUID postingId) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> new PostingNotFoundException(postingId));
        
        // 삭제 가능 상태 확인
        if (!isDeletable(posting.getPostingStatus())) {
            throw new IllegalStateException(
                String.format("삭제 불가능한 상태입니다: postingId=%s, status=%s. READY 또는 FAILED 상태만 삭제 가능합니다.", 
                    postingId, posting.getPostingStatus())
            );
        }
        
        log.info("[전표 삭제] postingId={}, status={}, type={}", 
            postingId, posting.getPostingStatus(), posting.getPostingType());
        
        postingRepository.delete(posting);
        
        log.info("[전표 삭제 완료] postingId={}", postingId);
    }
    
    /**
     * 일괄 전표 삭제
     * 
     * @param postingIds 전표 ID 목록
     * @return 삭제 결과 (성공/실패 개수 및 상세)
     */
    @Transactional
    public Map<String, Object> deletePostingsBatch(List<UUID> postingIds) {
        int success = 0;
        int failed = 0;
        List<Map<String, String>> details = new ArrayList<>();
        
        for (UUID postingId : postingIds) {
            try {
                deletePosting(postingId);
                success++;
                details.add(Map.of(
                    "postingId", postingId.toString(),
                    "status", "success"
                ));
            } catch (Exception e) {
                failed++;
                details.add(Map.of(
                    "postingId", postingId.toString(),
                    "status", "failed",
                    "error", e.getMessage()
                ));
                log.warn("[일괄 삭제 중 오류] postingId={}, error={}", postingId, e.getMessage());
            }
        }
        
        log.info("[일괄 전표 삭제 완료] total={}, success={}, failed={}", 
            postingIds.size(), success, failed);
        
        return Map.of(
            "success", success,
            "failed", failed,
            "total", postingIds.size(),
            "details", details
        );
    }
    
    /**
     * 전표 삭제 가능 여부 확인
     */
    private boolean isDeletable(PostingStatus status) {
        return status == PostingStatus.READY || 
               status == PostingStatus.FAILED ||
               status == PostingStatus.READY_TO_POST;
    }
    
    /**
     * requestPayload JSON에서 금액 추출
     * 이카운트 API 필드 규칙:
     * - Sale_tamt: 공급가액 (상품매출)
     * - Sale_samt: 배송비
     */
    private Long extractAmountFromRequestPayload(String requestPayload, PostingType postingType) {
        if (requestPayload == null || requestPayload.trim().isEmpty()) {
            return null;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(requestPayload);
            
            // 이카운트 판매 API 필드명 기준
            return switch (postingType) {
                case PRODUCT_SALES, PRODUCT_CANCEL -> {
                    if (root.has("Sale_tamt")) {
                        yield root.get("Sale_tamt").asLong(0);
                    }
                    yield null;
                }
                case SHIPPING_FEE, SHIPPING_FEE_CANCEL -> {
                    if (root.has("Sale_samt")) {
                        yield root.get("Sale_samt").asLong(0);
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("[requestPayload 파싱 실패] postingType={}, error={}", postingType, e.getMessage());
            return null;
        }
    }

    /**
     * OrderItem의 ProductMapping 확인
     * 
     * @param order 주문 엔티티
     * @return 매핑되지 않은 상품 목록 (productId:sku 형식)
     */
    private List<String> checkProductMappings(Order order) {
        List<String> unmappedItems = new ArrayList<>();

        if (order.getItems() == null || order.getItems().isEmpty()) {
            log.warn("[상품 아이템 없음] orderId={}", order.getOrderId());
            return unmappedItems;
        }

        log.info("[매핑 체크 시작] orderId={}, tenantId={}, storeId={}, marketplace={}, itemCount={}", 
            order.getOrderId(), order.getTenantId(), order.getStoreId(), order.getMarketplace(), order.getItems().size());

        for (com.sellsync.api.domain.order.entity.OrderItem item : order.getItems()) {
            log.info("[매핑 조회 시도] orderId={}, productId={}, sku={}, tenantId={}, storeId={}, marketplace={}", 
                order.getOrderId(), 
                item.getMarketplaceProductId(), 
                item.getMarketplaceSku(),
                order.getTenantId(),
                order.getStoreId(),
                order.getMarketplace());

            Optional<com.sellsync.api.domain.mapping.dto.ProductMappingResponse> mapping = 
                productMappingService.findActiveMapping(
                    order.getTenantId(),
                    order.getStoreId(),
                    order.getMarketplace(),
                    item.getMarketplaceProductId(),
                    item.getMarketplaceSku()
                );

            if (mapping.isEmpty()) {
                String itemKey = String.format("%s:%s", 
                    item.getMarketplaceProductId(), item.getMarketplaceSku());
                unmappedItems.add(itemKey);
                log.error("[상품 매핑 없음 또는 조건 불충족] orderId={}, productId={}, sku={}, tenantId={}, storeId={}, marketplace={} " +
                    "→ DB에서 매핑 데이터를 직접 확인해주세요. " +
                    "확인사항: (1) isActive=true 인지, (2) mappingStatus=MAPPED 인지, (3) productId/sku 값이 정확한지", 
                    order.getOrderId(), 
                    item.getMarketplaceProductId(), 
                    item.getMarketplaceSku(),
                    order.getTenantId(),
                    order.getStoreId(),
                    order.getMarketplace());
            } else {
                log.info("[매핑 조회 성공] orderId={}, productId={}, sku={}, erpItemCode={}", 
                    order.getOrderId(), 
                    item.getMarketplaceProductId(), 
                    item.getMarketplaceSku(),
                    mapping.get().getErpItemCode());
            }
        }

        return unmappedItems;
    }
}
