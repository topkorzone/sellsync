package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.SettlementCollectionStatus;
import com.sellsync.api.domain.order.exception.OrderNotFoundException;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.posting.dto.CreatePostingRequestDto;
import com.sellsync.api.domain.posting.dto.PostingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 전표 생성 Facade (순환 의존성 해소)
 *
 * PostingService ↔ OrderSettlementPostingService 순환 참조를 해소하기 위해
 * 주문 기반 전표 생성 시 정산 완료 여부에 따른 라우팅 로직을 담당합니다.
 *
 * 라우팅 규칙:
 * - 정산 완료(COLLECTED) 주문 → OrderSettlementPostingService (통합 전표)
 * - 쿠팡 주문 (정산 미수집 포함) → OrderSettlementPostingService (통합 전표, 예상 수수료 사용)
 * - 기타 일반 주문 → PostingService (기본 전표)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingFacadeService {

    private final PostingService postingService;
    private final OrderSettlementPostingService orderSettlementPostingService;
    private final OrderRepository orderRepository;

    /**
     * 주문 기반 전표 생성 (정산 라우팅 포함)
     *
     * @param orderId 주문 ID
     * @param request 전표 생성 요청
     * @return 생성된 전표 목록
     */
    public List<PostingResponse> createPostingsForOrder(UUID orderId, CreatePostingRequestDto request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 통합 전표 생성 대상:
        // 1. 정산 완료(COLLECTED) 주문 → 정산 데이터 기반 통합 전표
        // 2. 쿠팡 주문 (정산 미수집 포함) → 예상 수수료 기반 통합 전표
        //    (OrderSettlementPostingService에서 쿠팡 정산 미수집 허용)
        boolean useIntegrated = order.getSettlementStatus() == SettlementCollectionStatus.COLLECTED
                || order.getMarketplace() == Marketplace.COUPANG;

        if (useIntegrated) {
            String reason = order.getSettlementStatus() == SettlementCollectionStatus.COLLECTED
                    ? "정산 완료" : "쿠팡 주문 (예상 수수료 사용)";
            log.info("[통합 전표 생성 사용 - {}] orderId={}, bundleOrderId={}, settlementStatus={}",
                reason, orderId, order.getBundleOrderId(), order.getSettlementStatus());

            String bundleOrderId = order.getBundleOrderId() != null ?
                order.getBundleOrderId() : order.getMarketplaceOrderId();

            try {
                PostingResponse posting = orderSettlementPostingService.createPostingsForSettledOrder(
                    bundleOrderId, "ECOUNT"
                );
                return List.of(posting);
            } catch (Exception e) {
                log.error("[통합 전표 생성 실패 - 기본 방식으로 폴백] orderId={}, error={}",
                    orderId, e.getMessage(), e);
                // 폴백: 기존 방식으로 계속 진행
            }
        }

        // 일반 주문 또는 폴백: PostingService에 위임
        return postingService.createPostingsForOrder(orderId, request);
    }
}
