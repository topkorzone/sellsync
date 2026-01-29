package com.sellsync.api.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 설정 - 분산 환경 스케줄러 동시 실행 방지
 *
 * 복수 인스턴스 배포 시 동일 스케줄러가 동시에 실행되는 것을 방지합니다.
 * DB 기반 락을 사용하여 Redis 등 추가 인프라 없이 작동합니다.
 *
 * 시간 기준: JVM 시간 (Asia/Seoul, KST) 사용
 * - usingDbTime() 사용 시 UTC로 변환되어 KST 기반 시스템과 불일치 발생
 * - 단일 DB + 단일 시간대 환경에서는 JVM 시간 사용이 적합
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M") // 기본 최대 락 보유: 30분
public class ShedLockConfig {

    private final DataSource dataSource;

    /**
     * 애플리케이션 시작 시 shedlock 테이블 자동 생성
     * Flyway 비활성화 환경을 위한 자동 DDL
     */
    @PostConstruct
    public void createShedlockTableIfNotExists() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL,
                    lock_until TIMESTAMP NOT NULL,
                    locked_at TIMESTAMP NOT NULL,
                    locked_by VARCHAR(255) NOT NULL,
                    PRIMARY KEY (name)
                )
            """);
            log.info("[ShedLock] shedlock 테이블 확인/생성 완료");
        } catch (Exception e) {
            log.warn("[ShedLock] shedlock 테이블 생성 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }
    }

    @Bean
    public LockProvider lockProvider() {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // usingDbTime() 제거: UTC 대신 JVM 시간(KST) 사용
                        .build()
        );
    }
}
