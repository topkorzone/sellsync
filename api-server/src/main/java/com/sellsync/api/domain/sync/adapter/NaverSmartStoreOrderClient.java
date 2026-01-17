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
 * 네이버 스마트스토어 주문 수집 클라이언트 (Mock 구현)
 * 
 * 실제 구현 시:
 * - 네이버 커머스 API (commerce.naver.com) 연동
 * - OAuth 2.0 인증
 * - 주문 조회 API: /v1/orders
 */
@Slf4j
@Component
public class NaverSmartStoreOrderClient implements MarketplaceOrderClient {

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.NAVER_SMARTSTORE;
    }

    @Override
    public List<Order> fetchOrders(String storeCredentials, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("[Mock] 스마트스토어 주문 수집: {} ~ {}", startTime, endTime);
        
        // Mock: 테스트용 샘플 주문 생성
        List<Order> orders = new ArrayList<>();
        
        // 샘플 주문 1
        Order order1 = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("SMART-2026-0001")
                .orderStatus(OrderStatus.NEW)
                .orderedAt(startTime.plusHours(1))
                .paidAt(startTime.plusHours(1).plusMinutes(5))
                .totalProductAmount(50000L)
                .totalShippingAmount(3000L)
                .totalDiscountAmount(0L)
                .totalPaidAmount(53000L)
                .paymentMethod("CARD")
                .buyerName("홍길동")
                .buyerPhone("010-1234-5678")
                .receiverName("홍길동")
                .receiverPhone1("010-1234-5678")
                .receiverZipCode("12345")
                .receiverAddress("서울시 강남구 테헤란로 123 456호")
                .shippingFee(3000L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        // 샘플 주문 아이템 추가
        OrderItem item1 = OrderItem.builder()
                .lineNo(1)
                .marketplaceProductId("PROD-001")
                .marketplaceSku("SKU-001")
                .productName("테스트 상품")
                .optionName("블랙/L")
                .quantity(2)
                .unitPrice(25000L)
                .originalPrice(25000L)
                .discountAmount(0L)
                .lineAmount(50000L)
                .itemStatus(ItemStatus.NORMAL)
                .build();
        order1.addItem(item1);
        
        orders.add(order1);
        
        // 샘플 주문 2
        Order order2 = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId("SMART-2026-0002")
                .orderStatus(OrderStatus.PREPARING)
                .orderedAt(startTime.plusHours(2))
                .paidAt(startTime.plusHours(2).plusMinutes(3))
                .totalProductAmount(30000L)
                .totalShippingAmount(0L)
                .totalDiscountAmount(5000L)
                .totalPaidAmount(25000L)
                .paymentMethod("TRANSFER")
                .buyerName("김철수")
                .buyerPhone("010-9876-5432")
                .receiverName("김철수")
                .receiverPhone1("010-9876-5432")
                .receiverZipCode("54321")
                .receiverAddress("부산시 해운대구 해운대로 789 101동 202호")
                .shippingFee(0L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        OrderItem item2 = OrderItem.builder()
                .lineNo(1)
                .marketplaceProductId("PROD-002")
                .marketplaceSku("SKU-002")
                .productName("테스트 상품 2")
                .optionName("화이트/M")
                .quantity(1)
                .unitPrice(30000L)
                .originalPrice(35000L)
                .discountAmount(5000L)
                .lineAmount(30000L)
                .itemStatus(ItemStatus.NORMAL)
                .build();
        order2.addItem(item2);
        
        orders.add(order2);
        
        log.info("[Mock] 스마트스토어 주문 수집 완료: {} 건", orders.size());
        return orders;
    }

    @Override
    public Order fetchOrder(String storeCredentials, String marketplaceOrderId) {
        log.info("[Mock] 스마트스토어 단일 주문 조회: {}", marketplaceOrderId);
        
        // Mock: 단일 주문 반환
        Order order = Order.builder()
                .tenantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .marketplace(Marketplace.NAVER_SMARTSTORE)
                .marketplaceOrderId(marketplaceOrderId)
                .orderStatus(OrderStatus.NEW)
                .orderedAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now().plusMinutes(5))
                .totalProductAmount(50000L)
                .totalShippingAmount(3000L)
                .totalDiscountAmount(0L)
                .totalPaidAmount(53000L)
                .paymentMethod("CARD")
                .buyerName("홍길동")
                .buyerPhone("010-1234-5678")
                .receiverName("홍길동")
                .receiverPhone1("010-1234-5678")
                .receiverZipCode("12345")
                .receiverAddress("서울시 강남구 테헤란로 123 456호")
                .shippingFee(3000L)
                .rawPayload("{\"mock\": true}")
                .build();
        
        return order;
    }

    @Override
    public boolean testConnection(String storeCredentials) {
        log.info("[Mock] 스마트스토어 인증 테스트");
        // Mock: 항상 성공
        return true;
    }

    @Override
    public Integer getRemainingQuota() {
        // Mock: 스마트스토어는 rate limit 1000/min
        return 950;
    }
}
