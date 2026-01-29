package com.sellsync.api.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j 설정 - 외부 API 장애 전파 방지
 *
 * 마켓플레이스 API (네이버 스마트스토어, 쿠팡) 및 ERP API 호출에 대해
 * Circuit Breaker + 지수 백오프 재시도를 적용합니다.
 *
 * 장애 시나리오:
 * - API 타임아웃 → 30초 블로킹 → 스레드풀 고갈 → 연쇄 장애
 * - Circuit Breaker: 연속 실패 시 빠르게 실패 반환 (Fast Fail)
 * - Retry: 일시적 오류 시 지수 백오프로 재시도
 */
@Configuration
public class Resilience4jConfig {

    /**
     * 마켓플레이스 API용 Circuit Breaker
     *
     * 설정:
     * - 실패율 50% 이상 → OPEN (요청 차단)
     * - 대기 60초 → HALF_OPEN (일부 요청 허용)
     * - HALF_OPEN에서 3건 성공 → CLOSED (정상 복구)
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig marketplaceConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                              // 실패율 50% 이상이면 OPEN
                .slowCallRateThreshold(80)                             // 느린 호출 80% 이상이면 OPEN
                .slowCallDurationThreshold(Duration.ofSeconds(10))     // 10초 이상이면 느린 호출
                .waitDurationInOpenState(Duration.ofSeconds(60))       // OPEN → HALF_OPEN 대기 60초
                .permittedNumberOfCallsInHalfOpenState(3)              // HALF_OPEN 시 허용 호출 수
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)                                 // 최근 10건 기준
                .minimumNumberOfCalls(5)                               // 최소 5건 이상 호출 후 판단
                .recordExceptions(
                        IOException.class,
                        TimeoutException.class,
                        ResourceAccessException.class,
                        HttpServerErrorException.class
                )
                .build();

        return CircuitBreakerRegistry.of(marketplaceConfig);
    }

    @Bean
    public CircuitBreaker smartStoreCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("smartStore");
    }

    @Bean
    public CircuitBreaker coupangCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("coupang");
    }

    @Bean
    public CircuitBreaker ecountCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("ecount");
    }

    /**
     * 마켓플레이스 API용 Retry (지수 백오프)
     *
     * 설정:
     * - 최대 3회 재시도
     * - 초기 대기 2초 → 4초 → 8초 (지수 백오프)
     * - IOException, TimeoutException만 재시도
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig marketplaceRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofSeconds(2), 2.0))              // 2초 → 4초 → 8초
                .retryExceptions(
                        IOException.class,
                        TimeoutException.class,
                        ResourceAccessException.class
                )
                .build();

        return RetryRegistry.of(marketplaceRetryConfig);
    }

    @Bean
    public Retry smartStoreRetry(RetryRegistry registry) {
        return registry.retry("smartStore");
    }

    @Bean
    public Retry coupangRetry(RetryRegistry registry) {
        return registry.retry("coupang");
    }
}
