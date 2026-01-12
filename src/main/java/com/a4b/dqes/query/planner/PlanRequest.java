package com.a4b.dqes.query.planner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PlanRequest {
    private final String rootObjectCode;
    private final Set<String> requiredObjects;
    private final Set<String> filterOnlyObjects;

    public PlanRequest(String rootObjectCode, Set<String> requiredObjects, Set<String> filterOnlyObjects) {
        this.rootObjectCode = rootObjectCode;
        this.requiredObjects = unmodifiableCopy(requiredObjects);
        this.filterOnlyObjects = unmodifiableCopy(filterOnlyObjects);
    }

    private static Set<String> unmodifiableCopy(Set<String> s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(s));
    }

    public String rootObjectCode() { return rootObjectCode; }
    public Set<String> requiredObjects() { return requiredObjects; }
    public Set<String> filterOnlyObjects() { return filterOnlyObjects; }

    public boolean isFilterOnly(String obj) {
        return filterOnlyObjects.contains(obj) && !requiredObjects.contains(obj);
    }

    public Set<String> allNeededObjects() {
        Set<String> out = new HashSet<>(requiredObjects);
        out.addAll(filterOnlyObjects);
        return out;
    }
}
