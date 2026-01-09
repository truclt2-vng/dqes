/**
 * Created: May 26, 2025 2:02:04 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base.dao;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.googlecode.genericdao.dao.jpa.GenericDAO;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;

public interface ComGenericDAO<T, ID extends Serializable> extends  GenericDAO<T, ID> {

    GenericPredicateBuilder<T> predicateBuilder();

    T findByAggIdAndAuthStatus(UUID aggId, String authStatus, String appCode, String tenantCode);

    T findByAggId(UUID aggId, String appCode, String tenantCode);

    boolean existsUnauthorized(String code, Long excludeId, String appCode, String tenantCode);

    List<T> findUnAuthorizedByIds(List<Long> ids, String appCode, String tenantCode);

    T findById(Long id, String appCode, String tenantCode);

    List<T> findByIds(List<Long> ids, String appCode, String tenantCode);

    T findEffectiveByCode(String code, String appCode, String tenantCode);

    T findEffectiveByAggId(UUID aggId, String appCode, String tenantCode);

    boolean existsByCode(String code, String appCode, String tenantCode);

    List<T> findAll(Predicate predicate, String appCode, String tenantCode);

    List<T> findList(BooleanBuilder expr);

    T findOne(Predicate predicate, String appCode, String tenantCode);

    Stream<T> streamAll(String appCode, String tenantCode);
}
