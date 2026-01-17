package com.sellsync.api.domain.shipping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * SmartStore 송장 업데이트 Mock 클라이언트 (테스트용)
 * 
 * 테스트 시나리오:
 * - 정상 응답 반환
 * - 호출 횟수 추적 (동시성 테스트용)
 * - 실패 모드 설정 가능
 */
@Slf4j
@Component
public class MockSmartStoreShipmentClient implements SmartStoreShipmentClient {

    private final AtomicInteger callCount = new AtomicInteger(0);
    private boolean shouldFail = false;
    private String failureMessage = "Mock API failure";

    @Override
    public String updateTracking(String orderId, String carrierCode, String trackingNo) throws Exception {
        int count = callCount.incrementAndGet();
        log.info("[Mock SmartStore API] orderId={}, carrierCode={}, trackingNo={}, callCount={}", 
            orderId, carrierCode, trackingNo, count);

        // 실패 모드일 경우 예외 발생
        if (shouldFail) {
            throw new RuntimeException(failureMessage);
        }

        // 성공 응답 반환
        return String.format("{\"orderId\":\"%s\",\"carrierCode\":\"%s\",\"trackingNo\":\"%s\",\"status\":\"success\"}", 
            orderId, carrierCode, trackingNo);
    }

    /**
     * 호출 횟수 조회
     */
    public int getCallCount() {
        return callCount.get();
    }

    /**
     * 호출 횟수 리셋
     */
    public void resetCallCount() {
        callCount.set(0);
    }

    /**
     * 실패 모드 설정
     */
    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    /**
     * 실패 메시지 설정
     */
    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    /**
     * 모든 설정 리셋
     */
    public void reset() {
        resetCallCount();
        shouldFail = false;
        failureMessage = "Mock API failure";
    }
}
