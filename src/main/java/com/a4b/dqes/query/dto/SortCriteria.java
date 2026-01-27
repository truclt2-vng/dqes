package com.a4b.dqes.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Sort criteria for dynamic queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortCriteria {
    
    // @NotBlank(message = "Field is required")
    // private String field;
    
    private String dir = "ASC"; // ASC or DESC
    
    private String nulls; // NULLS_FIRST, NULLS_LAST
}
