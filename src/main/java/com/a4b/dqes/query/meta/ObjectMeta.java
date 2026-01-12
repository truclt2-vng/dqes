package com.a4b.dqes.query.meta;

import java.util.Objects;

public final class ObjectMeta {
    private final String objectCode;
    private final String dbTable;   // schema.table
    private final String aliasHint;

    public ObjectMeta(String objectCode, String dbTable, String aliasHint) {
        this.objectCode = Objects.requireNonNull(objectCode, "objectCode");
        this.dbTable = Objects.requireNonNull(dbTable, "dbTable");
        this.aliasHint = Objects.requireNonNull(aliasHint, "aliasHint");
    }

    public String objectCode() { return objectCode; }
    public String dbTable() { return dbTable; }
    public String aliasHint() { return aliasHint; }
}
