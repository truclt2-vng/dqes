package com.a4b.dqes.query.meta;

import java.util.Objects;

public final class OperationMeta {
    private final String code;
    private final String opSymbol;
    private final int arity;
    private final String valueShape;

    public OperationMeta(String code, String opSymbol, int arity, String valueShape) {
        this.code = Objects.requireNonNull(code, "code");
        this.opSymbol = opSymbol;
        this.arity = arity;
        this.valueShape = Objects.requireNonNull(valueShape, "valueShape");
    }

    public String code() { return code; }
    public String opSymbol() { return opSymbol; }
    public int arity() { return arity; }
    public String valueShape() { return valueShape; }
}
