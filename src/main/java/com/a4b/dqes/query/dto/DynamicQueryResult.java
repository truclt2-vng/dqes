package com.a4b.dqes.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result DTO for dynamic queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicQueryResult {
    
    private List<Map<String, Object>> data;
    
    private Long totalCount;
    
    private Integer offset;
    
    private Integer limit;
    
    private String executedSql;
    
    private Map<String, Object> parameters;
    
    private Long executionTimeMs;
}
