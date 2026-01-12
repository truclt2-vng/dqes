package com.a4b.dqes.query.planner;

import java.util.Objects;

import com.a4b.dqes.query.meta.JoinType;

public final class JoinJoinStep implements JoinStep {
    private final int relationId;
    private final JoinType joinType;
    private final String toObject;
    private final String toTable;
    private final String toAlias;
    private final String onClauseSql;

    public JoinJoinStep(int relationId, JoinType joinType, String toObject, String toTable, String toAlias, String onClauseSql) {
        this.relationId = relationId;
        this.joinType = Objects.requireNonNull(joinType, "joinType");
        this.toObject = Objects.requireNonNull(toObject, "toObject");
        this.toTable = Objects.requireNonNull(toTable, "toTable");
        this.toAlias = Objects.requireNonNull(toAlias, "toAlias");
        this.onClauseSql = Objects.requireNonNull(onClauseSql, "onClauseSql");
    }

    public int relationId() { return relationId; }
    public JoinType joinType() { return joinType; }
    public String toObject() { return toObject; }
    public String toTable() { return toTable; }
    public String toAlias() { return toAlias; }
    public String onClauseSql() { return onClauseSql; }

    @Override
    public String debug() {
        return "JOIN[" + joinType + "] " + toObject + " as " + toAlias + " ON " + onClauseSql;
    }
}
