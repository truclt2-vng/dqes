package com.a4b.dqes.query.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.a4b.dqes.query.meta.RelationInfo;

public final class Graph {
    private final Map<String, List<Edge>> out = new HashMap<>();

    public Graph(List<RelationInfo> relations) {
        for (RelationInfo r : relations) {
            Edge e = new Edge(r);
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }
        for (List<Edge> list : out.values()) {
            list.sort(Comparator.comparingInt(Edge::weight).thenComparing(Edge::code));
        }
    }

    public List<Edge> edgesFrom(String from) {
        return out.getOrDefault(from, List.of());
    }
}
