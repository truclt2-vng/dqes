package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbRelationJoinKey;

/**
 * Repository for RelationJoinKey with caching support and performance optimizations
 */
@Repository
public interface RelationJoinKeyRepository extends JpaRepository<QrytbRelationJoinKey, Integer> {
    
    // @Cacheable(value = "relationJoinKeysByDbconnId", key = "#dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbRelationJoinKey> findByDbconnIdOrderBySeq(Integer dbconnId);
}
