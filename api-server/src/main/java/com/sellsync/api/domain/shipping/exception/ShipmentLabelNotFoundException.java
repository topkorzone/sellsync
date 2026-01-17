package com.sellsync.api.domain.shipping.exception;

/**
 * 송장 발급 레코드 없음 예외
 */
public class ShipmentLabelNotFoundException extends RuntimeException {
    public ShipmentLabelNotFoundException(String message) {
        super(message);
    }
}
