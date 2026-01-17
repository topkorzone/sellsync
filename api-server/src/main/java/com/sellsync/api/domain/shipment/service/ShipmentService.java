package com.sellsync.api.domain.shipment.service;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.shipment.entity.Shipment;
import com.sellsync.api.domain.shipment.enums.CarrierCode;
import com.sellsync.api.domain.shipment.enums.MarketPushStatus;
import com.sellsync.api.domain.shipment.enums.ShipmentStatus;
import com.sellsync.api.domain.shipment.repository.ShipmentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    /**
     * 송장 수동 등록
     */
    @Transactional
    public Shipment createShipment(UUID tenantId, CreateShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .filter(o -> o.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // 택배사 코드 변환
        CarrierCode carrier = CarrierCode.resolve(request.getCarrierCode());

        // 중복 체크
        Optional<Shipment> existing = shipmentRepository
                .findByTenantIdAndOrderIdAndCarrierCodeAndTrackingNo(
                        tenantId, order.getOrderId(), carrier.getCode(), request.getTrackingNo());

        if (existing.isPresent()) {
            log.info("[Shipment] Already exists: order={}, tracking={}", 
                    request.getOrderId(), request.getTrackingNo());
            return existing.get();
        }

        Shipment shipment = Shipment.builder()
                .tenantId(tenantId)
                .storeId(order.getStoreId())
                .orderId(order.getOrderId())
                .carrierCode(carrier.getCode())
                .carrierName(carrier.getName())
                .trackingNo(request.getTrackingNo().replaceAll("[^0-9]", ""))
                .shipmentStatus(ShipmentStatus.INVOICE_CREATED)
                .marketPushStatus(MarketPushStatus.PENDING)
                .build();

        shipment = shipmentRepository.save(shipment);
        log.info("[Shipment] Created: id={}, order={}, tracking={}", 
                shipment.getShipmentId(), order.getOrderId(), request.getTrackingNo());

        return shipment;
    }

    /**
     * 송장 상세 조회
     */
    public ShipmentDetail getShipmentDetail(UUID tenantId, UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));

        Order order = orderRepository.findById(shipment.getOrderId())
                .orElse(null);

        return ShipmentDetail.builder()
                .shipment(shipment)
                .marketplaceOrderId(order != null ? order.getMarketplaceOrderId() : null)
                .marketplace(order != null ? order.getMarketplace().name() : null)
                .receiverName(order != null ? order.getReceiverName() : null)
                .build();
    }

    /**
     * 주문별 송장 조회
     */
    public List<Shipment> getShipmentsByOrder(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * 송장 목록 조회 (페이징)
     */
    public Page<Shipment> getShipments(UUID tenantId, ShipmentStatus status, Pageable pageable) {
        if (status != null) {
            return shipmentRepository.findByTenantIdAndShipmentStatusOrderByCreatedAtDesc(
                    tenantId, status, pageable);
        }
        return shipmentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    /**
     * 송장번호 수정 (마켓 반영 전에만 가능)
     */
    @Transactional
    public Shipment updateTrackingNo(UUID tenantId, UUID shipmentId, String newTrackingNo) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));

        if (shipment.getMarketPushStatus() == MarketPushStatus.SUCCESS) {
            throw new IllegalStateException("마켓에 반영된 송장은 수정할 수 없습니다");
        }

        shipment.setTrackingNo(newTrackingNo.replaceAll("[^0-9]", ""));
        shipment.setMarketPushStatus(MarketPushStatus.PENDING);  // 재반영 대기

        log.info("[Shipment] Updated tracking: id={}, newTracking={}", shipmentId, newTrackingNo);
        return shipmentRepository.save(shipment);
    }

    /**
     * 송장 삭제 (마켓 반영 전에만 가능)
     */
    @Transactional
    public void deleteShipment(UUID tenantId, UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));

        if (shipment.getMarketPushStatus() == MarketPushStatus.SUCCESS) {
            throw new IllegalStateException("마켓에 반영된 송장은 삭제할 수 없습니다");
        }

        shipmentRepository.delete(shipment);
        log.info("[Shipment] Deleted: id={}", shipmentId);
    }

    /**
     * 배송 완료 처리
     */
    @Transactional
    public Shipment markDelivered(UUID tenantId, UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));

        shipment.setShipmentStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());

        return shipmentRepository.save(shipment);
    }

    /**
     * 송장 통계
     */
    public ShipmentStats getStats(UUID tenantId) {
        long pending = shipmentRepository.countByTenantIdAndMarketPushStatus(
                tenantId, MarketPushStatus.PENDING);
        long pushing = shipmentRepository.countByTenantIdAndMarketPushStatus(
                tenantId, MarketPushStatus.PUSHING);
        long success = shipmentRepository.countByTenantIdAndMarketPushStatus(
                tenantId, MarketPushStatus.SUCCESS);
        long failed = shipmentRepository.countByTenantIdAndMarketPushStatus(
                tenantId, MarketPushStatus.FAILED);

        return ShipmentStats.builder()
                .pending(pending)
                .pushing(pushing)
                .success(success)
                .failed(failed)
                .total(pending + pushing + success + failed)
                .build();
    }

    // === Request/Response DTOs ===

    @Data
    @Builder
    public static class CreateShipmentRequest {
        private UUID orderId;
        private String carrierCode;
        private String trackingNo;
    }

    @Data
    @Builder
    public static class ShipmentDetail {
        private Shipment shipment;
        private String marketplaceOrderId;
        private String marketplace;
        private String receiverName;
    }

    @Data
    @Builder
    public static class ShipmentStats {
        private long pending;
        private long pushing;
        private long success;
        private long failed;
        private long total;
    }
}
