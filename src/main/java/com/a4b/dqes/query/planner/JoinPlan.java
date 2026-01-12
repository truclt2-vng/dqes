package com.a4b.dqes.query.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.a4b.dqes.query.meta.ObjectMeta;

public final class JoinPlan {
    private final String rootObject;
    private final ObjectMeta rootMeta;
    private final String rootAlias;

    private final Map<String, String> aliasByObject = new LinkedHashMap<>();
    private final List<JoinStep> steps = new ArrayList<>();

    public JoinPlan(String rootObject, ObjectMeta rootMeta, String rootAlias) {
        this.rootObject = Objects.requireNonNull(rootObject, "rootObject");
        this.rootMeta = Objects.requireNonNull(rootMeta, "rootMeta");
        this.rootAlias = Objects.requireNonNull(rootAlias, "rootAlias");
        aliasByObject.put(rootObject, rootAlias);
    }

    public String rootObject() { return rootObject; }
    public ObjectMeta rootMeta() { return rootMeta; }
    public String rootAlias() { return rootAlias; }

    public boolean hasAlias(String objectCode) { return aliasByObject.containsKey(objectCode); }
    public String aliasOf(String objectCode) { return aliasByObject.get(objectCode); }
    public void putAlias(String objectCode, String alias) { aliasByObject.put(objectCode, alias); }

    public List<JoinStep> steps() { return Collections.unmodifiableList(steps); }
    public void addStep(JoinStep s) { steps.add(s); }

    public Optional<ExistsStep> existsForTarget(String objectCode) {
        for (JoinStep s : steps) {
            if (s instanceof ExistsStep ex && ex.targetObject().equals(objectCode)) return Optional.of(ex);
        }
        return Optional.empty();
    }

    public String debugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROOT ").append(rootObject).append(" AS ").append(rootAlias).append("\\n");
        for (JoinStep s : steps) sb.append(" - ").append(s.debug()).append("\\n");
        sb.append("ALIASES ").append(aliasByObject).append("\\n");
        return sb.toString();
    }
}
