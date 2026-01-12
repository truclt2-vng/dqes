package com.a4b.dqes.query.util;

import java.util.Objects;

public final class DotPath {
    private DotPath() {}

    public static Parsed parse(String dot) {
        Objects.requireNonNull(dot, "dot");
        int i = dot.indexOf('.');
        if (i <= 0 || i >= dot.length() - 1) {
            throw new IllegalArgumentException("Invalid dot-path: " + dot + " (expected object.field)");
        }
        String obj = dot.substring(0, i).trim();
        String field = dot.substring(i + 1).trim();
        if (obj.isEmpty() || field.isEmpty()) {
            throw new IllegalArgumentException("Invalid dot-path: " + dot);
        }
        return new Parsed(obj, field);
    }

    public record Parsed(String object, String field) {}
}
