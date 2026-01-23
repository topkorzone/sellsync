package com.sellsync.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackages = "com.sellsync")
@EnableScheduling
public class ApiServerApplication {

    public static void main(String[] args) {
        // ✅ 애플리케이션 전체 Timezone을 한국 시간으로 설정
        // LocalDateTime.now()가 한국 시간을 반환하며, Hibernate가 DB 저장 시 UTC로 자동 변환
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        
        SpringApplication.run(ApiServerApplication.class, args);
    }
}
