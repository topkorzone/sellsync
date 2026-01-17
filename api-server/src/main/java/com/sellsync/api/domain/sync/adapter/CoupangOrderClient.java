package com.sellsync.api.domain.sync.adapter;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.ItemStatus;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.order.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 쿠팡 주문 수집 클라이언트 (Mock 구현)
 * 
 * 실제 구현 시:
 * - 쿠팡 Wing API (api-gateway.coupang.com) 연동
 * - HMAC 인증
 * - 주문 조회 API: /v2/providers/wing_api/apis/api/v1/marketplace/orders
 * 
 * @deprecated 실제 구현으로 대체됨 (com.sellsync.api.infra.marketplace.coupang.CoupangOrderClient)
 */
@Slf4j
// @Component  // 비활성화: 실제 구현(infra 패키지)과 충돌 방지
public class CoupangOrderClient implements MarketplaceOrderClient {

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.COUPANG;
    }

    @Override
    public List<Order> fetchOrders(String storeCredentials, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("[Mock] 쿠팡 주문 수집: {} ~ {}", startTime, endTime);
        
        // Mock: 테스트용 샘플 주문 생성
        List<Order> orders = new ArrayList<>();
        
        // 샘플 주문 1
        Order order1 = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.COUPANG)
                .marketplaceOrderId("CP-2026-0001")
                .orderStatus(OrderStatus.NEW)
                .orderedAt(startTime.plusHours(1))
                .paidAt(startTime.plusHours(1).plusMinutes(5))
                .totalProductAmount(80000L)
                .totalShippingAmount(0L)
                .totalDiscountAmount(10000L)
                .totalPaidAmount(70000L)
                .paymentMethod("CARD")
                .buyerName("이영희")
                .buyerPhone("010-2222-3333")
                .receiverName("이영희")
                .receiverPhone1("010-2222-3333")
                .receiverZipCode("13579")
                .receiverAddress("서울시 송파구 올림픽로 300 101동 505호")
                .shippingFee(0L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        // 샘플 주문 아이템 추가
        OrderItem item1 = OrderItem.builder()
                .lineNo(1)
                .marketplaceProductId("CP-PROD-001")
                .marketplaceSku("CP-SKU-001")
                .productName("쿠팡 테스트 상품")
                .optionName("레드/XL")
                .quantity(2)
                .unitPrice(40000L)
                .originalPrice(40000L)
                .discountAmount(5000L)
                .lineAmount(75000L)
                .itemStatus(ItemStatus.NORMAL)
                .build();
        order1.addItem(item1);
        
        orders.add(order1);
        
        // 샘플 주문 2
        Order order2 = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.COUPANG)
                .marketplaceOrderId("CP-2026-0002")
                .orderStatus(OrderStatus.SHIPPING)
                .orderedAt(startTime.plusHours(3))
                .paidAt(startTime.plusHours(3).plusMinutes(10))
                .totalProductAmount(45000L)
                .totalShippingAmount(2500L)
                .totalDiscountAmount(0L)
                .totalPaidAmount(47500L)
                .paymentMethod("CARD")
                .buyerName("박민수")
                .buyerPhone("010-4444-5555")
                .receiverName("박민수")
                .receiverPhone1("010-4444-5555")
                .receiverZipCode("24680")
                .receiverAddress("경기도 성남시 분당구 판교역로 166 A동 1001호")
                .shippingFee(2500L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        OrderItem item2 = OrderItem.builder()
                .lineNo(1)
                .marketplaceProductId("CP-PROD-002")
                .marketplaceSku("CP-SKU-002")
                .productName("쿠팡 테스트 상품 2")
                .optionName("블루/L")
                .quantity(1)
                .unitPrice(45000L)
                .originalPrice(45000L)
                .discountAmount(0L)
                .lineAmount(45000L)
                .itemStatus(ItemStatus.NORMAL)
                .build();
        order2.addItem(item2);
        
        orders.add(order2);
        
        log.info("[Mock] 쿠팡 주문 수집 완료: {} 건", orders.size());
        return orders;
    }

    @Override
    public Order fetchOrder(String storeCredentials, String marketplaceOrderId) {
        log.info("[Mock] 쿠팡 단일 주문 조회: {}", marketplaceOrderId);
        
        // Mock: 단일 주문 반환
        Order order = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.COUPANG)
                .marketplaceOrderId(marketplaceOrderId)
                .orderStatus(OrderStatus.NEW)
                .orderedAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now().plusMinutes(5))
                .totalProductAmount(80000L)
                .totalShippingAmount(0L)
                .totalDiscountAmount(10000L)
                .totalPaidAmount(70000L)
                .paymentMethod("CARD")
                .buyerName("이영희")
                .buyerPhone("010-2222-3333")
                .receiverName("이영희")
                .receiverPhone1("010-2222-3333")
                .receiverZipCode("13579")
                .receiverAddress("서울시 송파구 올림픽로 300 101동 505호")
                .shippingFee(0L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        return order;
    }

    @Override
    public boolean testConnection(String storeCredentials) {
        log.info("[Mock] 쿠팡 인증 테스트");
        // Mock: 항상 성공
        return true;
    }

    @Override
    public Integer getRemainingQuota() {
        // Mock: 쿠팡은 rate limit 500/min
        return 470;
    }
}
