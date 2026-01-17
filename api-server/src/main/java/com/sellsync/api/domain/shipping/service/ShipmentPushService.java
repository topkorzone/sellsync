package com.sellsync.api.domain.shipping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.OrderStatus;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.shipping.client.MarketShipmentClient;
import com.sellsync.api.domain.shipping.dto.ShipmentPushRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentPushResult;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.repository.ShipmentMarketPushRepository;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 송장 반영 서비스
 * 
 * 주요 기능:
 * - 단건 송장 반영
 * - 대기 중인 송장 일괄 반영
 * - 실패한 송장 재시도
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentPushService {

    private final ShipmentMarketPushRepository pushRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final List<MarketShipmentClient> marketClients;
    private final ObjectMapper objectMapper;

    /**
     * 단건 송장 반영
     */
    @Transactional
    public ShipmentPushResult pushShipment(UUID pushId) {
        log.info("[ShipmentPush] 단건 송장 반영 시작: pushId={}", pushId);

        ShipmentMarketPush push = pushRepository.findById(pushId)
                .orElseThrow(() -> new IllegalArgumentException("ShipmentMarketPush not found: " + pushId));

        // 이미 푸시 완료된 경우 멱등성 체크
        if (push.isAlreadyPushed()) {
            log.info("[ShipmentPush] 이미 푸시 완료된 송장: pushId={}", pushId);
            return ShipmentPushResult.success("Already pushed");
        }

        return executePush(push);
    }

    /**
     * 대기 중인 송장 일괄 반영 (MARKET_PUSH_REQUESTED 상태)
     */
    @Transactional
    public int pushPendingShipments(UUID tenantId) {
        log.info("[ShipmentPush] 대기 중인 송장 일괄 반영 시작: tenantId={}", tenantId);

        List<ShipmentMarketPush> pendingList = pushRepository.findPendingPushes(
                tenantId, 
                org.springframework.data.domain.PageRequest.of(0, 100)
        ).getContent();

        log.info("[ShipmentPush] 대기 송장 수: {} 건", pendingList.size());

        int successCount = 0;
        for (ShipmentMarketPush push : pendingList) {
            try {
                ShipmentPushResult result = executePush(push);
                if (result.isSuccess()) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("[ShipmentPush] 송장 반영 실패: pushId={}", push.getShipmentMarketPushId(), e);
            }
        }

        log.info("[ShipmentPush] 대기 송장 반영 완료: 성공={}/{}", successCount, pendingList.size());
        return successCount;
    }

    /**
     * 실패한 송장 재시도 (FAILED 상태 + 재시도 시각 도래)
     */
    @Transactional
    public int retryFailedShipments(UUID tenantId) {
        log.info("[ShipmentPush] 실패 송장 재시도 시작: tenantId={}", tenantId);

        List<ShipmentMarketPush> retryableList = pushRepository.findRetryablePushes(
                tenantId, 
                LocalDateTime.now()
        );

        log.info("[ShipmentPush] 재시도 대상 송장 수: {} 건", retryableList.size());

        int successCount = 0;
        for (ShipmentMarketPush push : retryableList) {
            try {
                // 재시도 준비 (FAILED -> MARKET_PUSH_REQUESTED)
                push.prepareRetry();
                pushRepository.save(push);

                ShipmentPushResult result = executePush(push);
                if (result.isSuccess()) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("[ShipmentPush] 재시도 실패: pushId={}", push.getShipmentMarketPushId(), e);
            }
        }

        log.info("[ShipmentPush] 재시도 완료: 성공={}/{}", successCount, retryableList.size());
        return successCount;
    }

    /**
     * 송장 반영 실행
     */
    private ShipmentPushResult executePush(ShipmentMarketPush push) {
        log.info("[ShipmentPush] 송장 반영 실행: pushId={}, orderId={}", 
                push.getShipmentMarketPushId(), push.getOrderId());

        try {
            // 주문 조회
            Order order = orderRepository.findById(push.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + push.getOrderId()));

            // 스토어 조회 (credentials 필요)
            Store store = storeRepository.findById(order.getStoreId())
                    .orElseThrow(() -> new IllegalArgumentException("Store not found: " + order.getStoreId()));

            // 마켓 클라이언트 찾기
            MarketShipmentClient client = findClientForMarketplace(push.getMarketplace());

            // 요청 데이터 구성
            ShipmentPushRequest request = buildPushRequest(push, order);
            String requestJson = objectMapper.writeValueAsString(request);
            push.setRequestPayload(requestJson);

            // API 호출
            ShipmentPushResult result = client.pushShipment(store.getCredentials(), request);

            // 결과 처리
            if (result.isSuccess()) {
                // 성공 처리
                push.markAsPushed(result.getRawResponse());
                
                // 주문 상태 업데이트
                if (order.getOrderStatus() == OrderStatus.CONFIRMED || 
                    order.getOrderStatus() == OrderStatus.PREPARING) {
                    order.setOrderStatus(OrderStatus.SHIPPING);
                    orderRepository.save(order);
                }

                log.info("[ShipmentPush] 송장 반영 성공: pushId={}, orderId={}", 
                        push.getShipmentMarketPushId(), order.getMarketplaceOrderId());
            } else {
                // 실패 처리
                push.markAsFailed(result.getErrorCode(), result.getErrorMessage());
                
                log.warn("[ShipmentPush] 송장 반영 실패: pushId={}, error={} - {}", 
                        push.getShipmentMarketPushId(), result.getErrorCode(), result.getErrorMessage());
            }

            pushRepository.save(push);
            return result;

        } catch (Exception e) {
            log.error("[ShipmentPush] 송장 반영 오류: pushId={}", push.getShipmentMarketPushId(), e);
            
            // 예외 발생 시 실패 처리
            push.markAsFailed("SYSTEM_ERROR", e.getMessage());
            pushRepository.save(push);
            
            return ShipmentPushResult.failure("SYSTEM_ERROR", e.getMessage());
        }
    }

    /**
     * 마켓플레이스에 맞는 클라이언트 찾기
     */
    private MarketShipmentClient findClientForMarketplace(com.sellsync.api.domain.order.enums.Marketplace marketplace) {
        return marketClients.stream()
                .filter(client -> client.getMarketplace() == marketplace)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 마켓플레이스: " + marketplace));
    }

    /**
     * 송장 반영 요청 데이터 구성
     */
    private ShipmentPushRequest buildPushRequest(ShipmentMarketPush push, Order order) {
        ShipmentPushRequest.ShipmentPushRequestBuilder builder = ShipmentPushRequest.builder()
                .marketplaceOrderId(push.getMarketplaceOrderId())
                .carrierCode(push.getCarrierCode())
                .trackingNo(push.getTrackingNo());

        // 마켓별 추가 정보 설정
        switch (push.getMarketplace()) {
            case NAVER_SMARTSTORE:
                // 스마트스토어: productOrderId 필요
                String productOrderId = extractProductOrderId(order);
                builder.productOrderId(productOrderId);
                break;
                
            case COUPANG:
                // 쿠팡: shipmentBoxId 필요
                String shipmentBoxId = extractShipmentBoxId(order);
                builder.shipmentBoxId(shipmentBoxId);
                break;
                
            default:
                log.warn("[ShipmentPush] 지원하지 않는 마켓: {}", push.getMarketplace());
        }

        return builder.build();
    }

    /**
     * 스마트스토어 productOrderId 추출
     * 
     * 실제 구현 시 Order의 rawPayload에서 추출하거나,
     * OrderItem에 저장된 marketplaceProductId를 사용
     */
    private String extractProductOrderId(Order order) {
        // 간단한 구현: marketplaceOrderId를 그대로 사용
        // 실제로는 rawPayload JSON 파싱 필요
        return order.getMarketplaceOrderId();
    }

    /**
     * 쿠팡 shipmentBoxId 추출
     * 
     * 실제 구현 시 Order의 rawPayload에서 추출
     */
    private String extractShipmentBoxId(Order order) {
        // 간단한 구현: marketplaceOrderId를 그대로 사용
        // 실제로는 rawPayload JSON 파싱 필요
        return order.getMarketplaceOrderId();
    }
}
