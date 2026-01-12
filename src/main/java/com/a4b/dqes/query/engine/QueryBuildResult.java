package com.a4b.dqes.query.engine;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class QueryBuildResult {
    private final String sql;
    private final Map<String, Object> params;

    public QueryBuildResult(String sql, Map<String, Object> params) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.params = Collections.unmodifiableMap(Objects.requireNonNull(params, "params"));
    }

    public String sql() { return sql; }
    public Map<String, Object> params() { return params; }
}
