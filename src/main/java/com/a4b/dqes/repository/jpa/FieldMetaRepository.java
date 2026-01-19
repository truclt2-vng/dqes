package com.a4b.dqes.repository.jpa;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.a4b.dqes.domain.FieldMeta;

import jakarta.persistence.QueryHint;

/**
 * Repository for FieldMeta with caching support and performance optimizations
 */
@Repository
public interface FieldMetaRepository extends JpaRepository<FieldMeta, Integer> {

    @Cacheable(value = "fieldMetaByDbconnId", key = "#dbconnId")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<FieldMeta> findByDbconnId(Integer dbconnId);
}
