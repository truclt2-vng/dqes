package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbOperationMeta;

/**
 * Repository for OperationMeta with caching support and performance optimizations
 */
@Repository
public interface OperationMetaJpaRepository extends JpaRepository<QrytbOperationMeta, Integer> {
    
    // @Cacheable(value = "operationMetaAll", key = "#tenantCode + '_' + #appCode")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbOperationMeta> findByTenantCodeAndAppCode(
        String tenantCode, String appCode
    );
}
