package com.sellsync.api;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackages = "com.sellsync")
@EnableScheduling
public class ApiServerApplication {

    public static void main(String[] args) {
        // ✅ 애플리케이션 전체 Timezone을 한국 시간으로 설정
        // LocalDateTime.now()가 한국 시간을 반환하며, 로그도 한국 시간으로 출력됨
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        
        SpringApplication app = new SpringApplication(ApiServerApplication.class);

        app.addListeners((org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent event) -> {
            var env = event.getEnvironment();
            System.out.println("[BOOT] spring.flyway.url=" + env.getProperty("spring.flyway.url"));
            System.out.println("[BOOT] spring.flyway.user=" + env.getProperty("spring.flyway.user"));
            System.out.println("[BOOT] spring.flyway.password=" + (env.getProperty("spring.flyway.password") != null ? "***" : "null"));

            System.out.println("[BOOT] spring.profiles.active=" + String.join(",", env.getActiveProfiles()));
            System.out.println("[BOOT] spring.datasource.url=" + env.getProperty("spring.datasource.url"));
            System.out.println("[BOOT] spring.datasource.username=" + env.getProperty("spring.datasource.username"));
            System.out.println("[BOOT] spring.datasource.password=" + (env.getProperty("spring.datasource.password") != null ? "***" : "null"));
        });

        app.run(args);
    }



    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties props) {
        HikariDataSource ds = props.initializeDataSourceBuilder()
          .type(HikariDataSource.class)
          .build();

        System.out.println("[REAL] hikari.jdbcUrl=" + ds.getJdbcUrl());
        System.out.println("[REAL] hikari.username=" + ds.getUsername());
        System.out.println("[REAL] hikari.password.len=" + (ds.getPassword() == null ? "null" : ds.getPassword().length()));

        return ds;
    }


}
