package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.OperationMeta;

import jakarta.persistence.QueryHint;

/**
 * Repository for OperationMeta with caching support and performance optimizations
 */
@Repository
public interface OperationMetaRepository extends JpaRepository<OperationMeta, Integer> {
    
    @Cacheable(value = "operationMetaAll", key = "#tenantCode + '_' + #appCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<OperationMeta> findByTenantCodeAndAppCode(
        String tenantCode, String appCode
    );
}
