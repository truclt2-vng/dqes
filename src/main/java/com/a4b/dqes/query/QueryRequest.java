package com.a4b.dqes.query;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for dynamic query execution
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    private String tenantCode;
    private String appCode;
    private Integer dbconnId;
    private String rootObject;         // Or use alias hint (e.g., "emp")
    
    private List<String> selectFields;  // Alias-based format: ["emp.emp_name", "dept.dept_name"]
    private List<Filter> filters;
    private List<Sort> sorts;
    
    private Integer limit;
    private Integer offset;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {
        private String field;             // Or alias-based format: "emp.status"
        private String operatorCode;      // EQ, IN, BETWEEN, etc.
        private Object value;             // Single value or List for IN/BETWEEN
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sort {
        private String field;             // Or alias-based format: "emp.emp_name"
        private SortDirection direction = SortDirection.ASC;
    }

    public enum SortDirection {
        ASC, DESC
    }
}
