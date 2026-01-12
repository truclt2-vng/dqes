package com.a4b.dqes.query.meta;

import java.util.Objects;

public final class RelationJoinKey {
    private final int relationId;
    private final int seq;

    private final String fromColumnName;
    private final String operator;
    private final String toColumnName;

    private final boolean nullSafe;

    public RelationJoinKey(int relationId, int seq, String fromColumnName, String operator, String toColumnName, boolean nullSafe) {
        this.relationId = relationId;
        this.seq = seq;
        this.fromColumnName = Objects.requireNonNull(fromColumnName, "fromColumnName");
        this.operator = Objects.requireNonNull(operator, "operator");
        this.toColumnName = Objects.requireNonNull(toColumnName, "toColumnName");
        this.nullSafe = nullSafe;
        if (seq < 1) throw new IllegalArgumentException("seq must be >= 1");
    }

    public int relationId() { return relationId; }
    public int seq() { return seq; }
    public String fromColumnName() { return fromColumnName; }
    public String operator() { return operator; }
    public String toColumnName() { return toColumnName; }
    public boolean nullSafe() { return nullSafe; }
}
