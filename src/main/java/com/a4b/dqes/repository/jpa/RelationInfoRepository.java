package com.a4b.dqes.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbRelationInfo;

/**
 * Repository for RelationInfo with caching support for graph traversal
 * Optimized with query hints for better performance
 */
@Repository
public interface RelationInfoRepository extends JpaRepository<QrytbRelationInfo, Integer> {


    // @Cacheable(value = "relationInfoByDbconnId", key = "#dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbRelationInfo> findByDbconnId(Integer dbconnId);


    //Review
    // @Cacheable(value = "relationInfoByCode", key = "#tenantCode + '_' + #appCode + '_' + #code",unless = "#result == null")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<QrytbRelationInfo> findByTenantCodeAndAppCodeAndCode(
        String tenantCode, String appCode, String code
    );


    //Review
    @Query("SELECT r FROM QrytbRelationInfo r WHERE r.tenantCode = :tenantCode " +
           "AND r.appCode = :appCode AND r.dbconnId = :dbconnId " +
           "AND r.isNavigable = true")
    // @Cacheable(value = "relationInfoNavigable", key = "#tenantCode + '_' + #appCode + '_' + #dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbRelationInfo> findNavigableRelations(
        @Param("tenantCode") String tenantCode,
        @Param("appCode") String appCode,
        @Param("dbconnId") Integer dbconnId
    );
}
