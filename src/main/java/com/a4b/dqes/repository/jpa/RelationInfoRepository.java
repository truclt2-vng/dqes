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


    @Cacheable(value = "relationInfoByDbconnId", key = "#dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationInfo> findByDbconnId(Integer dbconnId);


    //Review
    @Cacheable(value = "relationInfoByCode", key = "#tenantCode + '_' + #appCode + '_' + #code")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<RelationInfo> findByTenantCodeAndAppCodeAndCode(
        String tenantCode, String appCode, String code
    );


    //Review
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
}
