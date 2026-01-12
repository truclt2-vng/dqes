package com.a4b.dqes.query.planner;

import java.util.HashMap;
import java.util.Map;

import com.a4b.dqes.query.meta.ObjectMeta;

public final class AliasAllocator {
    private final Map<String, Integer> counters = new HashMap<>();

    public String alloc(ObjectMeta meta) {
        String hint = meta.aliasHint();
        String prefix = "t";
        if (hint != null && !hint.isBlank()) {
            char c = Character.toLowerCase(hint.trim().charAt(0));
            if (Character.isLetter(c)) prefix = String.valueOf(c);
        }
        int n = counters.merge(prefix, 1, Integer::sum) - 1;
        return prefix + n;
    }
}
