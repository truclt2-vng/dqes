package com.a4b.dqes.repository.jpa;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.RelationInfo;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RelationInfo with caching support for graph traversal
 * Optimized with query hints for better performance
 */
@Repository
public interface RelationInfoRepository extends JpaRepository<RelationInfo, Integer> {
    
    @Cacheable(value = "relationInfoByCode", key = "#tenantCode + '_' + #appCode + '_' + #code")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<RelationInfo> findByTenantCodeAndAppCodeAndCode(
        String tenantCode, String appCode, String code
    );
    
    @Cacheable(value = "relationInfoByFromObject", key = "#tenantCode + '_' + #appCode + '_' + #fromObjectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationInfo> findByTenantCodeAndAppCodeAndFromObjectCode(
        String tenantCode, String appCode, String fromObjectCode
    );
    
    @Cacheable(value = "relationInfoByToObject", key = "#tenantCode + '_' + #appCode + '_' + #toObjectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationInfo> findByTenantCodeAndAppCodeAndToObjectCode(
        String tenantCode, String appCode, String toObjectCode
    );
    
    @Cacheable(value = "relationInfoByPair", key = "#tenantCode + '_' + #appCode + '_' + #fromObjectCode + '_' + #toObjectCode")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationInfo> findByTenantCodeAndAppCodeAndFromObjectCodeAndToObjectCode(
        String tenantCode, String appCode, String fromObjectCode, String toObjectCode
    );
    
    @Query("SELECT r FROM RelationInfo r WHERE r.tenantCode = :tenantCode " +
           "AND r.appCode = :appCode AND r.dbconnId = :dbconnId " +
           "AND r.isNavigable = true")
    @Cacheable(value = "relationInfoNavigable", key = "#tenantCode + '_' + #appCode + '_' + #dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationInfo> findNavigableRelations(
        @Param("tenantCode") String tenantCode,
        @Param("appCode") String appCode,
        @Param("dbconnId") Integer dbconnId
    );
    
    /**
     * Find relation by join_alias
     * Used for resolving user-provided aliases to specific relations
     */
    @Cacheable(value = "relationInfoByJoinAlias", key = "#tenantCode + '_' + #appCode + '_' + #joinAlias")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<RelationInfo> findByTenantCodeAndAppCodeAndJoinAlias(
        String tenantCode, String appCode, String joinAlias
    );
}
