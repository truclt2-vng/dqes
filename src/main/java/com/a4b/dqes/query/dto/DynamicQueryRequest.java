package com.a4b.dqes.query.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for dynamic queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicQueryRequest {
    
    @NotNull(message = "Database connection ID is required")
    private Integer dbconnId;
    
    @Hidden
    @JsonIgnore
    private String rootObject;
    
    @Valid
    private List<String> selectFields;
    
    @Valid
    private List<FilterCriteria> filters;
    
    @Valid
    private Map<String, SortCriteria> sorts;
    
    @Min(value = 0, message = "Offset must be non-negative")
    @Builder.Default
    private Integer offset = 0;
    
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    @Builder.Default
    private Integer limit = 50;
    
    @Builder.Default
    private Boolean distinct = false;
    
    @Builder.Default
    private Boolean countOnly = false;
}
