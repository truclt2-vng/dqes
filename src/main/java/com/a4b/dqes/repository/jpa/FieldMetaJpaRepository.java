package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbFieldMeta;

/**
 * Repository for FieldMeta with caching support and performance optimizations
 */
@Repository
public interface FieldMetaJpaRepository extends JpaRepository<QrytbFieldMeta, Integer> {

    // @Cacheable(value = "fieldMetaByDbconnId", key = "#dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbFieldMeta> findByDbconnId(Integer dbconnId);
}
