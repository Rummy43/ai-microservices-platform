package com.ramesh.user_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class AuditConfig {
    // This activates the @CreatedDate and @LastModifiedDate annotations
}
