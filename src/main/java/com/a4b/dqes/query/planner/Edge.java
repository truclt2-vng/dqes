package com.a4b.dqes.query.planner;

import java.util.Objects;

import com.a4b.dqes.domain.RelationInfo;

public class Edge {
    private final RelationInfo r;

    public Edge(RelationInfo r) {
        this.r = Objects.requireNonNull(r, "relation");
    }

    public int id() { return r.getId(); }
    public String code() { return r.getCode(); }
    public String fromObject() { return r.getFromObjectCode(); }
    public String toObject() { return r.getToObjectCode(); }
    public String relationType() { return r.getRelationType(); }
    public String joinType() { return r.getJoinType(); }
    public String joinAlias() { return r.getJoinAlias(); }
    public int weight() { return r.getPathWeight(); }
}
