/**
 * Created: Nov 27, 2023 3:40:09 PM
 * Copyright © 2023 by A4B. All Rights Reserved
 */
package com.a4b.dqes.config;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@RegisterDefaultEntities(packages = {"org.axonframework.eventsourcing.eventstore.jpa",
        "org.axonframework.eventhandling.tokenstore",
        "org.axonframework.modelling.saga.repository.jpa"})
@Configuration
public class AxonJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource) throws SQLException {
        return new SQLErrorCodesResolver(dataSource);
    }

	@ConditionalOnMissingBean({EventStorageEngine.class, EventStore.class})
    @Bean
    public EventStorageEngine eventStorageEngine(Serializer serializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 org.axonframework.config.Configuration configuration,
                                                 EntityManagerProvider entityManagerProvider,
                                                 TransactionManager transactionManager) {
        return JpaEventStorageEngine.builder()
                .snapshotSerializer(serializer)
                .upcasterChain(configuration.upcasterChain())
                .persistenceExceptionResolver(persistenceExceptionResolver)
                .eventSerializer(eventSerializer)
                .snapshotFilter(configuration.snapshotFilter())
                .entityManagerProvider(entityManagerProvider)
                .transactionManager(transactionManager)
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    @Primary
    public EntityManagerProvider entityManagerProvider() {
        return new AxonEntityManagerProvider();
    }

	@ConditionalOnMissingBean
	@Bean
	public TokenStore tokenStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
		return JpaTokenStore.builder().entityManagerProvider(entityManagerProvider).serializer(serializer).build();
	}

	@ConditionalOnMissingBean(SagaStore.class)
	@Bean
	public JpaSagaStore sagaStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
		return JpaSagaStore.builder().entityManagerProvider(entityManagerProvider).serializer(serializer).build();
	}
}
