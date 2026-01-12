package com.a4b.dqes.query.dto;

import java.util.Objects;

public final class FilterSpec {
    public String field;        // "worker.code"
    public String operatorCode; // "EQ"
    public Object value;        // can be null for IS_NULL

    public void validateBasic() {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operatorCode, "operatorCode");
    }
}
