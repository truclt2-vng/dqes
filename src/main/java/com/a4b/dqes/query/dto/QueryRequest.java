package com.a4b.dqes.query.dto;

import java.util.List;
import java.util.Objects;

public final class QueryRequest {
    public String tenantCode;
    public String appCode;
    public Integer dbconnId;

    public String rootObject;

    public List<String> selectFields;
    public List<FilterSpec> filters;

    public Integer offset;
    public Integer limit;

    public void validateBasic() {
        Objects.requireNonNull(tenantCode, "tenantCode");
        Objects.requireNonNull(appCode, "appCode");
        Objects.requireNonNull(dbconnId, "dbconnId");
        Objects.requireNonNull(rootObject, "rootObject");
        Objects.requireNonNull(selectFields, "selectFields");
        if (offset == null) offset = 0;
        if (limit == null) limit = 100;
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
    }
}
