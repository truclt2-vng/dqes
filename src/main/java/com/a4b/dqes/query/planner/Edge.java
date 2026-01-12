package com.a4b.dqes.query.planner;

import java.util.Objects;

import com.a4b.dqes.query.meta.FilterMode;
import com.a4b.dqes.query.meta.JoinType;
import com.a4b.dqes.query.meta.RelationInfo;
import com.a4b.dqes.query.meta.RelationType;

public final class Edge {
    private final RelationInfo r;

    public Edge(RelationInfo r) {
        this.r = Objects.requireNonNull(r, "relation");
    }

    public int id() { return r.id(); }
    public String code() { return r.code(); }
    public String from() { return r.from(); }
    public String to() { return r.to(); }
    public RelationType relationType() { return r.relationType(); }
    public JoinType joinType() { return r.joinType(); }
    public FilterMode filterMode() { return r.filterMode(); }
    public boolean navigable() { return r.navigable(); }
    public int weight() { return r.pathWeight(); }
}
