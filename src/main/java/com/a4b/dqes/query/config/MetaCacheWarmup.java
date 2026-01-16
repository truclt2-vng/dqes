/**
 * Created: Jan 13, 2026 2:09:03 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.config;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.a4b.dqes.repository.jpa.FieldMetaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MetaCacheWarmup implements ApplicationRunner {
    private final ObjectMetaRepository objectMetaRepository;
    private final FieldMetaRepository fieldMetaRepository;
    private final RelationInfoRepository relationInfoRepository;

    @Override
    public void run(ApplicationArguments args) {
        // gọi method có @Cacheable -> tự đổ vào cache
        String tenant = "SUPPER";
        String app = "SUPPER";
        CompletableFuture.allOf(
            warmObject(tenant, app),
            warmField(tenant, app),
            warmRelationInfo(tenant, app)
        ).join();
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmObject(String tenant, String app) {
        objectMetaRepository.findByTenantCodeAndAppCode(tenant, app);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmField(String tenant, String app) {
        fieldMetaRepository.findByTenantCodeAndAppCode(tenant, app);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmRelationInfo(String tenant, String app) {
        relationInfoRepository.findByTenantCodeAndAppCode(tenant, app);
        return CompletableFuture.completedFuture(null);
    }
}
