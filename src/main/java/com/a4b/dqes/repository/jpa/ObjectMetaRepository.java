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

    @Cacheable(value = "objectMetaByTenantCodeAppCode", key = "#tenantCode + '_' + #appCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<ObjectMeta> findByTenantCodeAndAppCode(String tenantCode, String appCode);
}
