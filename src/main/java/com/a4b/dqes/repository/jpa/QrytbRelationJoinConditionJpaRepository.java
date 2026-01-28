package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbRelationJoinCondition;

/**
 * Repository for QrytbRelationJoinCondition with caching support and performance optimizations
 */
@Repository
public interface QrytbRelationJoinConditionJpaRepository extends JpaRepository<QrytbRelationJoinCondition, Integer> {
    
    // @Cacheable(value = "relationJoinConditionsByDbconnId", key = "#dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbRelationJoinCondition> findByDbconnIdOrderBySeq(Integer dbconnId);
}
