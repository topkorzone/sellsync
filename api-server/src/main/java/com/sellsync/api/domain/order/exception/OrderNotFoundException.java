package com.sellsync.api.domain.order.exception;

import java.util.UUID;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super(String.format("Order not found: %s", orderId));
    }

    public OrderNotFoundException(String message) {
        super(message);
    }
}
