package com.sellsync.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = {"com.sellsync.api.domain", "com.sellsync.api.infra"})
@EnableJpaAuditing
@EnableTransactionManagement
public class JpaConfig {
}
