package com.sellsync.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 실행 설정
 * 
 * - SyncJob 비동기 실행을 위한 ThreadPool 설정
 * - 최대 동시 실행 수: 10개
 * - 큐 크기: 100개
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * SyncJob 전용 Executor
     */
    @Bean(name = "syncJobTaskExecutor")
    public Executor syncJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);           // 기본 스레드 수
        executor.setMaxPoolSize(10);           // 최대 스레드 수
        executor.setQueueCapacity(100);        // 큐 크기
        executor.setThreadNamePrefix("sync-"); // 스레드 이름 접두사
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("SyncJob Executor 초기화: corePoolSize=5, maxPoolSize=10, queueCapacity=100");
        
        return executor;
    }

    /**
     * Posting 전송 전용 Executor
     */
    @Bean(name = "postingTaskExecutor")
    public Executor postingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);            // 기본 스레드 수 (ERP API 부하 고려)
        executor.setMaxPoolSize(5);             // 최대 스레드 수
        executor.setQueueCapacity(50);          // 큐 크기
        executor.setThreadNamePrefix("post-");  // 스레드 이름 접두사
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("Posting Executor 초기화: corePoolSize=3, maxPoolSize=5, queueCapacity=50");
        
        return executor;
    }
}
