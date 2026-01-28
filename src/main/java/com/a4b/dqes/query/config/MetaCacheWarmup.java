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

import com.a4b.dqes.query.service.DbSchemaCacheService;
import com.a4b.dqes.repository.jpa.FieldMetaJpaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaJpaRepository;
import com.a4b.dqes.repository.jpa.QrytbRelationJoinConditionJpaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;
import com.a4b.dqes.repository.jpa.RelationJoinKeyRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MetaCacheWarmup implements ApplicationRunner {
    private final DbSchemaCacheService dbSchemaCacheService;
    private final ObjectMetaJpaRepository objectMetaRepository;
    private final FieldMetaJpaRepository fieldMetaRepository;
    private final RelationInfoRepository relationInfoRepository;
    private final RelationJoinKeyRepository relationJoinKeyRepository;
    private final QrytbRelationJoinConditionJpaRepository relationJoinConditionRepository;

    @Override
    public void run(ApplicationArguments args) {
        dbSchemaCacheService.loadDbSchemaCache("hcmcore");
        // dbSchemaCacheService.loadDbSchemaCache("hcmcore");
        // gọi method có @Cacheable -> tự đổ vào cache
        // CompletableFuture.allOf(
        //     warmObject(1),
        //     warmField(1),
        //     warmRelationInfo(1),
        //     warmRelationJoinKey(1),
        //     warmRelationJoinCondition(1)
        // ).join();
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmObject(Integer dbconnId) {
        objectMetaRepository.findByDbconnId(dbconnId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmField(Integer dbconnId) {
        fieldMetaRepository.findByDbconnId(dbconnId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmRelationInfo(Integer dbconnId) {
        relationInfoRepository.findByDbconnId(dbconnId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmRelationJoinKey(Integer dbconnId) {
        relationJoinKeyRepository.findByDbconnIdOrderBySeq(dbconnId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Void> warmRelationJoinCondition(Integer dbconnId) {
        relationJoinConditionRepository.findByDbconnIdOrderBySeq(dbconnId);
        return CompletableFuture.completedFuture(null);
    }
}