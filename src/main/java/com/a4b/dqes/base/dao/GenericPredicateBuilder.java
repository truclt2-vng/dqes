package com.a4b.dqes.base.dao;

import java.util.Collection;

import org.springframework.util.StringUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanPath;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.JPQLQuery;

public class GenericPredicateBuilder<T> {

    private final PathBuilder<T> entityPath;
    private final BooleanBuilder builder = new BooleanBuilder();
    private BooleanBuilder currentGroup = new BooleanBuilder();
    private boolean useOr = false; // false = AND, true = OR
    private boolean aclCheck = false;

    public GenericPredicateBuilder(Class<T> clazz) {
        String entityName = clazz.getSimpleName();
        String alias = StringUtils.uncapitalize(entityName);
        this.entityPath = new PathBuilder<>(clazz, alias);
    }

    public GenericPredicateBuilder(Class<T> clazz, String alias) {
        this.entityPath = new PathBuilder<>(clazz, alias);
    }

    public GenericPredicateBuilder<T> enableAclCheck(boolean enabled) {
        this.aclCheck = enabled;
        return this;
    }

    public GenericPredicateBuilder<T> or() {
        flushCurrentGroup();
        useOr = true;
        currentGroup = new BooleanBuilder();
        return this;
    }

    public GenericPredicateBuilder<T> and() {
        flushCurrentGroup();
        useOr = false;
        currentGroup = new BooleanBuilder();
        return this;
    }

    private void flushCurrentGroup() {
        if (currentGroup.hasValue()) {
            if (useOr) {
                builder.or(currentGroup);
            } else {
                builder.and(currentGroup);
            }
        }
    }

    public <V> GenericPredicateBuilder<T> eq(String field, V value) {
        Class<V> type = (Class<V>) (value != null ? value.getClass() : Object.class);
        if (type == Boolean.class) {
            BooleanPath path = entityPath.getBoolean(field);
            if (Boolean.TRUE.equals(value)) {
                currentGroup.and(path.isTrue());
            } else if (Boolean.FALSE.equals(value)) {
                currentGroup.and(path.isFalse());
            } else {
                currentGroup.and(path.isNull());
            }
        } else {
            if (value != null) {
                currentGroup.and(entityPath.get(field, type).eq(value));
            } else {
                currentGroup.and(entityPath.get(field, type).isNull());
            }
        }
        return this;
    }


    public GenericPredicateBuilder<T> like(String field, String value) {
        StringPath path = entityPath.getString(field);
        if (value != null) {
            currentGroup.and(path.containsIgnoreCase(value));
        } else {
            currentGroup.and(path.isNull());
        }
        return this;
    }


    public <V> GenericPredicateBuilder<T> in(String field, Collection<V> values) {
        if (values == null || values.isEmpty()) {
            currentGroup.and(entityPath.get(field, Object.class).isNull());
        } else {
            V sample = values.iterator().next();
            currentGroup.and(entityPath.get(field, (Class<V>) sample.getClass()).in(values));
        }
        return this;
    }


    public <V extends Comparable<?>> GenericPredicateBuilder<T> between(String field, V start, V end) {
        ComparableExpression<V> path = entityPath.getComparable(field, (Class<V>) (start != null ? start.getClass() : end.getClass()));
        if (start != null && end != null) {
            currentGroup.and(path.between(start, end));
        } else if (start != null) {
            currentGroup.and(path.goe(start));
        } else if (end != null) {
            currentGroup.and(path.loe(end));
        }
        return this;
    }


    public <V extends Comparable<?>> GenericPredicateBuilder<T> gt(String field, V value) {
        if (value != null) {
            currentGroup.and(entityPath.getComparable(field, (Class<V>) value.getClass()).gt(value));
        }
        return this;
    }

    public <V extends Comparable<?>> GenericPredicateBuilder<T> lt(String field, V value) {
        if (value != null) {
            currentGroup.and(entityPath.getComparable(field, (Class<V>) value.getClass()).lt(value));
        }
        return this;
    }

    public <V extends Comparable<?>> GenericPredicateBuilder<T> goe(String field, V value) {
        if (value != null) {
            currentGroup.and(entityPath.getComparable(field, (Class<V>) value.getClass()).goe(value));
        }
        return this;
    }

    public <V extends Comparable<?>> GenericPredicateBuilder<T> loe(String field, V value) {
        if (value != null) {
            currentGroup.and(entityPath.getComparable(field, (Class<V>) value.getClass()).loe(value));
        }
        return this;
    }

    public GenericPredicateBuilder<T> exists(JPQLQuery<?> subQuery) {
        currentGroup.and(subQuery.exists());
        return this;
    }

    public GenericPredicateBuilder<T> notExists(JPQLQuery<?> subQuery) {
        currentGroup.and(subQuery.notExists());
        return this;
    }

    public Predicate build() {
        flushCurrentGroup(); // ensure last group is applied
        return builder.getValue();
    }

    public PathBuilder<T> path() {
        return entityPath;
    }
}

/**
 * 
    Predicate predicate = new GenericPredicateBuilder<>(User.class)
    .eq("status", "ACTIVE")
    .or()
    .like("email", "gmail")
    .like("email", "yahoo")
    .and()
    .gt("age", 18)
    .build();

    (status = 'ACTIVE') AND ((email LIKE '%gmail%') OR (email LIKE '%yahoo%')) AND (age > 18)

    =================================

    Predicate predicate = new GenericPredicateBuilder<>(User.class)
    .in("role", List.of("ADMIN", "USER"), String.class)
    .or()
    .in("departmentId", List.of(1L, 2L, 3L), Long.class)
    .build();

    ===================

    QEmployee employee = QEmployee.employee;
    QLeaveRequest leave = QLeaveRequest.leaveRequest;

    JPQLQuery<?> subQuery = JPAExpressions
        .selectOne()
        .from(leave)
        .where(leave.employeeId.eq(employee.id)
            .and(leave.status.eq("APPROVED")));

    GenericPredicateBuilder<Employee> builder = new GenericPredicateBuilder<>(employee);
    builder.exists(subQuery);

    
 */