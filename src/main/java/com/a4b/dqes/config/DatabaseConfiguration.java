/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories({ "com.a4b.dqes.repository", "com.a4b.core.ezmt.client.dataconstraint.repository" })
@EntityScan(value = { "com.a4b.dqes.domain", "com.a4b.core.ezmt.client.dataconstraint.entity", "com.a4b.dqes.agg",
        "org.axonframework.modelling.saga.repository.jpa", "org.axonframework.eventhandling.saga.repository.jpa",
        "org.axonframework.eventsourcing.eventstore.jpa", "org.axonframework.eventhandling.tokenstore.jpa" })
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {
        
}
