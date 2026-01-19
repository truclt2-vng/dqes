package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.ObjectMeta;

import jakarta.persistence.QueryHint;

/**
 * Repository for ObjectMeta with caching support and performance optimizations
 */
@Repository
public interface ObjectMetaRepository extends JpaRepository<ObjectMeta, Integer> {

    @Cacheable(value = "objectMetaByDbconnId", key = "#dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<ObjectMeta> findByDbconnId(Integer dbconnId);
}
