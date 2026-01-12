package com.a4b.dqes.query.planner;

import java.util.Objects;

public final class ExistsStep implements JoinStep {
    public static final String FILTER_TOKEN = "/*__FILTER__*/";

    private final String targetObject;
    private final String targetAlias;
    private final String existsSql;

    public ExistsStep(String targetObject, String targetAlias, String existsSql) {
        this.targetObject = Objects.requireNonNull(targetObject, "targetObject");
        this.targetAlias = Objects.requireNonNull(targetAlias, "targetAlias");
        this.existsSql = Objects.requireNonNull(existsSql, "existsSql");
        if (!existsSql.contains(FILTER_TOKEN)) {
            throw new IllegalArgumentException("existsSql must contain FILTER_TOKEN");
        }
    }

    public String targetObject() { return targetObject; }
    public String targetAlias() { return targetAlias; }
    public String existsSql() { return existsSql; }

    @Override
    public String debug() {
        return "EXISTS target=" + targetObject + " alias=" + targetAlias;
    }
}
