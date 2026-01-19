/**
 * Created: Jan 19, 2026 5:10:30 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import java.util.Objects;

public final class DotPath {
    private DotPath() {}

    public static FieldKey parse(String dot) {
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
        return new FieldKey(obj, field);
    }
}
