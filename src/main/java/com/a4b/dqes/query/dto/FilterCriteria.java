package com.a4b.dqes.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Filter criteria for dynamic queries
 * Supports standard operators: EQ, NE, GT, LT, GTE, LTE, IN, NOT_IN, 
 * LIKE, NOT_LIKE, IS_NULL, IS_NOT_NULL, BETWEEN, EXISTS, NOT_EXISTS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterCriteria {
    
    @NotBlank(message = "Field is required")
    private String field;
    
    @NotBlank(message = "Operator code is required")
    private String operatorCode;
    
    private Object value;
    
    private Object value2; // For BETWEEN operator
    
    private List<Object> values; // For IN, NOT_IN operators
    
    // For nested filters (AND/OR grouping)
    private String logicalOperator; // AND, OR
    private List<FilterCriteria> subFilters;
    
    // For EXISTS/NOT_EXISTS subqueries
    private DynamicQueryRequest subQuery;
}
