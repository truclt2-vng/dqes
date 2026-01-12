package com.a4b.dqes.query.ast;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Root AST node representing a complete query
 * Maps to: SELECT {selects} FROM {root} {joins} WHERE {filters} ORDER BY {sorts}
 */
@Data
public class QueryAST {
    private String tenantCode;
    private String appCode;
    private Integer dbconnId;
    
    // Root object (FROM clause)
    private String rootObject;
    
    // SELECT clause nodes
    private List<SelectNode> selects = new ArrayList<>();
    
    // WHERE clause nodes (AND-combined)
    private List<FilterNode> filters = new ArrayList<>();
    
    // ORDER BY clause nodes
    private List<SortNode> sorts = new ArrayList<>();
    
    // JOIN graph (computed by JoinPathPlanner)
    private List<JoinNode> joins = new ArrayList<>();
    
    // Pagination
    private Integer limit;
    private Integer offset;
    
    public void addSelect(SelectNode node) {
        this.selects.add(node);
    }
    
    public void addFilter(FilterNode node) {
        this.filters.add(node);
    }
    
    public void addSort(SortNode node) {
        this.sorts.add(node);
    }
    
    public void addJoin(JoinNode node) {
        this.joins.add(node);
    }
}
