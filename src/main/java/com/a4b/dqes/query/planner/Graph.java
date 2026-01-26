package com.a4b.dqes.query.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.a4b.dqes.domain.RelationInfo;

public class Graph {
    private Map<String, List<Edge>> out = new HashMap<>();

    public Graph(List<RelationInfo> relations) {
        for (RelationInfo r : relations) {
            Edge e = new Edge(r);
            out.computeIfAbsent(e.fromObject(), k -> new ArrayList<>()).add(e);
        }
        for (List<Edge> list : out.values()) {
            list.sort(Comparator.comparingInt(Edge::weight).thenComparing(Edge::code));
        }
    }

    public List<Edge> edgesFrom(String fromObject) {
        return out.getOrDefault(fromObject, List.of());
    }
}
