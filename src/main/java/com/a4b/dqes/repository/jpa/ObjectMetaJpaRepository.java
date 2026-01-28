package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.QrytbObjectMeta;

/**
 * Repository for ObjectMeta with caching support and performance optimizations
 */
@Repository
public interface ObjectMetaJpaRepository extends JpaRepository<QrytbObjectMeta, Integer> {

    // @Cacheable(value = "objectMetaByDbconnId", key = "#dbconnId",unless = "#result == null || #result.isEmpty()")
    // @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<QrytbObjectMeta> findByDbconnId(Integer dbconnId);
}
