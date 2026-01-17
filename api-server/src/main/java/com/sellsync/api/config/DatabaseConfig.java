package com.sellsync.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import lombok.extern.slf4j.Slf4j;

/**
 * 데이터베이스 초기화 설정
 * 
 * 애플리케이션 시작 시 데이터베이스를 초기화하고 마이그레이션을 실행합니다.
 * 
 * 사용법:
 * 1. application-local.yml에 "spring.profiles.active: local,db-reset" 설정
 * 2. 또는 실행 시: ./gradlew bootRun --args='--spring.profiles.active=local,db-reset'
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    /**
     * DB 초기화 프로파일이 활성화된 경우 Flyway clean 후 migrate 실행
     * 주의: 모든 데이터가 삭제됩니다!
     */
    @Bean
    @Profile("db-reset")
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            log.warn("=".repeat(80));
            log.warn("⚠️  DB 초기화 시작: 모든 데이터가 삭제됩니다!");
            log.warn("=".repeat(80));
            
            try {
                flyway.clean();
                log.info("✅ Flyway Clean 완료");
                
                flyway.migrate();
                log.info("✅ Flyway Migrate 완료");
                
                log.warn("=".repeat(80));
                log.warn("✅ DB 초기화 및 마이그레이션 완료!");
                log.warn("=".repeat(80));
            } catch (Exception e) {
                log.error("❌ DB 초기화 실패: {}", e.getMessage(), e);
                throw e;
            }
        };
    }
}
