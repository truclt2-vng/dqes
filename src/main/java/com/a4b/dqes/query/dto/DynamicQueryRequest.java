package com.a4b.dqes.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for dynamic queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicQueryRequest {
    
    @NotBlank(message = "Tenant code is required")
    private String tenantCode;
    
    @NotBlank(message = "App code is required")
    private String appCode;
    
    @NotNull(message = "Database connection ID is required")
    private Integer dbconnId;
    
    @NotBlank(message = "Root object is required")
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
