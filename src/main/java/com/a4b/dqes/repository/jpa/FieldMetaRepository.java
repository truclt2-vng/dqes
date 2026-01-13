package com.a4b.dqes.repository.jpa;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.FieldMeta;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FieldMeta with caching support and performance optimizations
 */
@Repository
public interface FieldMetaRepository extends JpaRepository<FieldMeta, Integer> {
    
    @Cacheable(value = "fieldMetaByObject", key = "#tenantCode + '_' + #appCode + '_' + #objectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<FieldMeta> findByTenantCodeAndAppCodeAndObjectCode(
        String tenantCode, String appCode, String objectCode
    );

    List<FieldMeta> findByTenantCodeAndAppCodeAndObjectCodeIn(
        String tenantCode, String appCode, List<String> objectCodes
    );
    
    @Cacheable(value = "fieldMetaByCode", key = "#tenantCode + '_' + #appCode + '_' + #objectCode + '_' + #fieldCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<FieldMeta> findByTenantCodeAndAppCodeAndObjectCodeAndFieldCode(
        String tenantCode, String appCode, String objectCode, String fieldCode
    );
    
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    // List<FieldMeta> findByTenantCodeAndAppCodeAndDbconnId(
    //     String tenantCode, String appCode, Integer dbconnId
    // );
}
