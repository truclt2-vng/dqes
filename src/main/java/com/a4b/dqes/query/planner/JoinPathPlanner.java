package com.a4b.dqes.query.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.a4b.dqes.query.meta.FilterMode;
import com.a4b.dqes.query.meta.ObjectMeta;
import com.a4b.dqes.query.meta.RelationJoinKey;
import com.a4b.dqes.query.meta.RelationType;
import com.a4b.dqes.query.util.Ident;

public final class JoinPathPlanner {

    private final AliasAllocator aliasAllocator = new AliasAllocator();

    public JoinPlan plan(
            PlanRequest req,
            Graph graph,
            Map<String, ObjectMeta> objectMetaByCode,
            Map<Integer, List<RelationJoinKey>> joinKeysByRelationId
    ) {
        Objects.requireNonNull(req, "req");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(objectMetaByCode, "objectMetaByCode");
        Objects.requireNonNull(joinKeysByRelationId, "joinKeysByRelationId");

        String root = req.rootObjectCode();
        ObjectMeta rootMeta = mustMeta(objectMetaByCode, root);

        JoinPlan out = new JoinPlan(root, rootMeta, aliasAllocator.alloc(rootMeta));

        Set<String> needed = req.allNeededObjects();
        needed.remove(root);

        Map<String, List<Edge>> pathByTarget = new LinkedHashMap<>();
        for (String target : needed) {
            List<Edge> path = shortestPathBfsHopThenWeight(root, target, graph);
            if (path == null) throw new IllegalStateException("No join path: " + root + " -> " + target);
            pathByTarget.put(target, path);
        }

        // JOIN for required objects
        Set<Edge> joinEdgeSet = new LinkedHashSet<>();
        for (String target : req.requiredObjects()) {
            if (target.equals(root)) continue;
            List<Edge> p = pathByTarget.get(target);
            if (p != null) joinEdgeSet.addAll(p);
        }
        emitJoinEdgesInOrder(joinEdgeSet, out, objectMetaByCode, joinKeysByRelationId);

        // EXISTS/JOIN for filter-only
        for (String filterTarget : req.filterOnlyObjects()) {
            if (filterTarget.equals(root)) continue;
            if (!req.isFilterOnly(filterTarget)) continue;

            List<Edge> path = pathByTarget.get(filterTarget);
            if (path == null || path.isEmpty()) continue;

            JoinMode mode = decideModeForFilterOnlyPath(path);

            if (mode == JoinMode.JOIN) {
                emitJoinEdgesInOrder(new LinkedHashSet<>(path), out, objectMetaByCode, joinKeysByRelationId);
            } else {
                ExistsStep ex = buildExistsForPath(out, path, objectMetaByCode, joinKeysByRelationId);
                out.addStep(ex);
            }
        }

        return out;
    }

    /* BFS hop-first, weight tie-break */
    private List<Edge> shortestPathBfsHopThenWeight(String start, String target, Graph graph) {
        if (start.equals(target)) return List.of();

        Map<String, Prev> bestPrev = new HashMap<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        q.add(start);
        bestPrev.put(start, new Prev(null, null, 0, 0));

        while (!q.isEmpty()) {
            String u = q.poll();
            Prev pu = bestPrev.get(u);

            for (Edge e : graph.edgesFrom(u)) {
                if (!e.navigable()) continue;

                String v = e.to();
                int newHops = pu.hops + 1;
                int newWeight = pu.weightSum + e.weight();
                Prev cur = bestPrev.get(v);

                boolean better = (cur == null)
                        || (newHops < cur.hops)
                        || (newHops == cur.hops && newWeight < cur.weightSum);

                if (better) {
                    bestPrev.put(v, new Prev(u, e, newHops, newWeight));
                    q.add(v);
                }
            }
        }

        Prev pt = bestPrev.get(target);
        if (pt == null || pt.prevEdge == null) return null;

        List<Edge> rev = new ArrayList<>();
        String cur = target;
        while (!cur.equals(start)) {
            Prev p = bestPrev.get(cur);
            if (p == null || p.prevNode == null || p.prevEdge == null) return null;
            rev.add(p.prevEdge);
            cur = p.prevNode;
        }
        Collections.reverse(rev);
        return rev;
    }

    private record Prev(String prevNode, Edge prevEdge, int hops, int weightSum) {}

    private void emitJoinEdgesInOrder(
            Set<Edge> edges,
            JoinPlan out,
            Map<String, ObjectMeta> objectMetaByCode,
            Map<Integer, List<RelationJoinKey>> joinKeysByRelationId
    ) {
        if (edges.isEmpty()) return;

        Set<Edge> remaining = new LinkedHashSet<>(edges);
        boolean progressed;

        do {
            progressed = false;

            for (Edge e : new ArrayList<>(remaining)) {
                if (!out.hasAlias(e.from())) continue;

                if (!out.hasAlias(e.to())) {
                    ObjectMeta toMeta = mustMeta(objectMetaByCode, e.to());
                    out.putAlias(e.to(), aliasAllocator.alloc(toMeta));
                }

                String fromAlias = out.aliasOf(e.from());
                String toAlias = out.aliasOf(e.to());
                ObjectMeta toMeta = mustMeta(objectMetaByCode, e.to());

                String on = buildPredicate(e.id(), fromAlias, toAlias, joinKeysByRelationId);

                out.addStep(new JoinJoinStep(
                        e.id(),
                        e.joinType(),
                        e.to(),
                        toMeta.dbTable(),
                        toAlias,
                        on
                ));

                remaining.remove(e);
                progressed = true;
            }
        } while (progressed);

        if (!remaining.isEmpty()) {
            String left = remaining.stream().map(Edge::code).collect(Collectors.joining(","));
            throw new IllegalStateException("Cannot emit joins (cycle/unreachable). Remaining: " + left);
        }
    }

    private String buildPredicate(int relationId, String fromAlias, String toAlias, Map<Integer, List<RelationJoinKey>> joinKeysByRelationId) {
        List<RelationJoinKey> keys = joinKeysByRelationId.get(relationId);
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("Missing join keys for relation_id=" + relationId);
        }

        return keys.stream()
                .sorted(Comparator.comparingInt(RelationJoinKey::seq))
                .map(k -> {
                    String left = Ident.col(fromAlias, k.fromColumnName());
                    String right = Ident.col(toAlias, k.toColumnName());

                    if (k.nullSafe()) {
                        if ("=".equals(k.operator())) return left + " IS NOT DISTINCT FROM " + right;
                        if ("!=".equals(k.operator())) return left + " IS DISTINCT FROM " + right;
                    }
                    return left + " " + k.operator() + " " + right;
                })
                .collect(Collectors.joining(" AND "));
    }

    private JoinMode decideModeForFilterOnlyPath(List<Edge> path) {
        boolean anyJoinOnly = path.stream().anyMatch(e -> e.filterMode() == FilterMode.JOIN_ONLY);
        boolean anyExistsOnly = path.stream().anyMatch(e -> e.filterMode() == FilterMode.EXISTS_ONLY);
        if (anyJoinOnly) return JoinMode.JOIN;
        if (anyExistsOnly) return JoinMode.EXISTS;

        boolean anyExistsPreferred = path.stream().anyMatch(e -> e.filterMode() == FilterMode.EXISTS_PREFERRED);
        if (anyExistsPreferred) return JoinMode.EXISTS;

        boolean risky = path.stream().anyMatch(e ->
                e.filterMode() == FilterMode.AUTO &&
                        (e.relationType() == RelationType.ONE_TO_MANY || e.relationType() == RelationType.MANY_TO_MANY)
        );
        return risky ? JoinMode.EXISTS : JoinMode.JOIN;
    }

    private ExistsStep buildExistsForPath(
            JoinPlan out,
            List<Edge> path,
            Map<String, ObjectMeta> objectMetaByCode,
            Map<Integer, List<RelationJoinKey>> joinKeysByRelationId
    ) {
        AliasAllocator local = new AliasAllocator();
        Map<String, String> localAliasByObj = new HashMap<>();

        Edge first = path.getFirst();
        if (!out.hasAlias(first.from())) {
            throw new IllegalStateException("Outer alias missing for EXISTS anchor: " + first.from());
        }
        String outerFromAlias = out.aliasOf(first.from());

        String baseObj = first.to();
        ObjectMeta baseMeta = mustMeta(objectMetaByCode, baseObj);
        String baseAlias = local.alloc(baseMeta);
        localAliasByObj.put(baseObj, baseAlias);

        StringBuilder fromJoin = new StringBuilder();
        fromJoin.append("FROM ")
                .append(Ident.quoteSchemaTable(baseMeta.dbTable()))
                .append(" ")
                .append(baseAlias)
                .append(" ");

        String linkPredicate = buildPredicate(first.id(), outerFromAlias, baseAlias, joinKeysByRelationId);

        for (int i = 1; i < path.size(); i++) {
            Edge e = path.get(i);

            String subFromObj = e.from();
            String subToObj = e.to();

            String subFromAlias = localAliasByObj.get(subFromObj);
            if (subFromAlias == null) {
                ObjectMeta m = mustMeta(objectMetaByCode, subFromObj);
                subFromAlias = local.alloc(m);
                localAliasByObj.put(subFromObj, subFromAlias);
            }

            ObjectMeta toMeta = mustMeta(objectMetaByCode, subToObj);
            String subToAlias = local.alloc(toMeta);
            localAliasByObj.put(subToObj, subToAlias);

            String on = buildPredicate(e.id(), subFromAlias, subToAlias, joinKeysByRelationId);

            fromJoin.append(e.joinType().name())
                    .append(" JOIN ")
                    .append(Ident.quoteSchemaTable(toMeta.dbTable()))
                    .append(" ")
                    .append(subToAlias)
                    .append(" ON ")
                    .append(on)
                    .append(" ");
        }

        String targetObj = path.getLast().to();
        String targetAlias = localAliasByObj.get(targetObj);

        String existsSql = "EXISTS (SELECT 1 " + fromJoin + "WHERE " + linkPredicate + " " + ExistsStep.FILTER_TOKEN + ")";

        return new ExistsStep(targetObj, targetAlias, existsSql);
    }

    private ObjectMeta mustMeta(Map<String, ObjectMeta> map, String objectCode) {
        ObjectMeta m = map.get(objectCode);
        if (m == null) throw new IllegalStateException("Missing object_meta for object_code=" + objectCode);
        return m;
    }
}
