package com.a4b.dqes.repository.jpa;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.ObjectMeta;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ObjectMeta with caching support and performance optimizations
 */
@Repository
public interface ObjectMetaRepository extends JpaRepository<ObjectMeta, Integer> {
    
    @Cacheable(value = "objectMetaByCode", key = "#tenantCode + '_' + #appCode + '_' + #objectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<ObjectMeta> findByTenantCodeAndAppCodeAndObjectCode(
        String tenantCode, String appCode, String objectCode
    );

    @Cacheable(value = "objectMetaByAliasHint", key = "#tenantCode + '_' + #appCode + '_' + #aliasHint")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<ObjectMeta> findByTenantCodeAndAppCodeAndAliasHint(
        String tenantCode, String appCode, String aliasHint
    );

    Optional<List<ObjectMeta>> findByTenantCodeAndAppCodeAndAliasHintIn(
        String tenantCode, String appCode, List<String> aliasHints
    );
    
    @Cacheable(value = "objectMetaByDbconn", key = "#tenantCode + '_' + #appCode + '_' + #dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<ObjectMeta> findByTenantCodeAndAppCodeAndDbconnId(
        String tenantCode, String appCode, Integer dbconnId
    );
    
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<ObjectMeta> findByTenantCodeAndAppCode(
        String tenantCode, String appCode
    );
    
    /**
     * Optimized projection query for table name lookup
     */
    @Query("SELECT o.dbTable FROM ObjectMeta o WHERE o.tenantCode = :tenantCode " +
           "AND o.appCode = :appCode AND o.objectCode = :objectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<String> findDbTableByCode(
        @Param("tenantCode") String tenantCode,
        @Param("appCode") String appCode,
        @Param("objectCode") String objectCode
    );
}
