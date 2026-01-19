package com.a4b.dqes.repository.jpa;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.RelationJoinKey;

import jakarta.persistence.QueryHint;
import java.util.List;

/**
 * Repository for RelationJoinKey with caching support and performance optimizations
 */
@Repository
public interface RelationJoinKeyRepository extends JpaRepository<RelationJoinKey, Integer> {
    
    @Cacheable(value = "relationJoinKeysByDbconnId", key = "#dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<RelationJoinKey> findByDbconnIdOrderBySeq(Integer dbconnId);
}
