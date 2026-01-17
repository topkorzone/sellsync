package com.sellsync.api.domain.shipping;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.entity.ShipmentMarketPush;
import com.sellsync.api.domain.shipping.enums.MarketPushStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * MarketPush 도메인 통합 테스트 베이스 (T-001-3)
 * - Testcontainers를 사용한 실제 PostgreSQL 환경 테스트
 */
@SpringBootTest
@Testcontainers
public abstract class MarketPushTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("sellsync_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        // 테스트별 격리가 필요한 경우 여기서 처리
    }

    /**
     * 테스트용 ShipmentMarketPush 생성 헬퍼
     */
    protected ShipmentMarketPush createPush(MarketPushStatus status) {
        return ShipmentMarketPush.builder()
            .tenantId(UUID.randomUUID())
            .orderId(UUID.randomUUID())
            .trackingNo("TEST-TRACKING-" + UUID.randomUUID())
            .marketplace(Marketplace.NAVER_SMARTSTORE)
            .marketplaceOrderId("MARKETPLACE-ORDER-" + UUID.randomUUID())
            .carrierCode("CJ")
            .pushStatus(status)
            .attemptCount(0)
            .build();
    }
}
