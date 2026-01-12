package com.a4b.dqes.query.meta;

import java.util.Objects;

public final class RelationInfo {
    private final int id;
    private final String code;
    private final String fromObjectCode;
    private final String toObjectCode;

    private final RelationType relationType;
    private final JoinType joinType;
    private final FilterMode filterMode;

    private final boolean required;
    private final boolean navigable;
    private final int pathWeight;

    public RelationInfo(
            int id,
            String code,
            String fromObjectCode,
            String toObjectCode,
            RelationType relationType,
            JoinType joinType,
            FilterMode filterMode,
            boolean required,
            boolean navigable,
            int pathWeight
    ) {
        this.id = id;
        this.code = Objects.requireNonNull(code, "code");
        this.fromObjectCode = Objects.requireNonNull(fromObjectCode, "fromObjectCode");
        this.toObjectCode = Objects.requireNonNull(toObjectCode, "toObjectCode");
        this.relationType = Objects.requireNonNull(relationType, "relationType");
        this.joinType = Objects.requireNonNull(joinType, "joinType");
        this.filterMode = Objects.requireNonNull(filterMode, "filterMode");
        this.required = required;
        this.navigable = navigable;
        this.pathWeight = pathWeight;
        if (pathWeight < 0) throw new IllegalArgumentException("pathWeight must be >= 0");
    }

    public int id() { return id; }
    public String code() { return code; }
    public String from() { return fromObjectCode; }
    public String to() { return toObjectCode; }
    public RelationType relationType() { return relationType; }
    public JoinType joinType() { return joinType; }
    public FilterMode filterMode() { return filterMode; }
    public boolean required() { return required; }
    public boolean navigable() { return navigable; }
    public int pathWeight() { return pathWeight; }
}
