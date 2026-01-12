package com.a4b.dqes.query;

import com.a4b.dqes.query.ast.SortNode.SortDirection;
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
    private String rootObjectCode;      // Direct object code (e.g., "EMPLOYEE")
    private String rootObject;         // Or use alias hint (e.g., "emp")
    
    private List<String> selectFields;  // Alias-based format: ["emp.emp_name", "dept.dept_name"]
    private List<Filter> filters;
    private List<Sort> sorts;
    
    private Integer limit;
    private Integer offset;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectField {
        private String objectCode;      // Direct codes (e.g., "EMPLOYEE", "NAME")
        private String fieldCode;
        private String alias;
        private String field;           // Or alias-based format: "emp.emp_name"
        
        public SelectField(String objectCode, String fieldCode) {
            this.objectCode = objectCode;
            this.fieldCode = fieldCode;
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {
        private String objectCode;        // Direct codes (e.g., "EMPLOYEE", "STATUS")
        private String fieldCode;
        private String operatorCode;      // EQ, IN, BETWEEN, etc.
        private Object value;             // Single value or List for IN/BETWEEN
        private String field;             // Or alias-based format: "emp.status"
        
        public Filter(String objectCode, String fieldCode, String operatorCode) {
            this.objectCode = objectCode;
            this.fieldCode = fieldCode;
            this.operatorCode = operatorCode;
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sort {
        private String objectCode;        // Direct codes (e.g., "EMPLOYEE", "NAME")
        private String fieldCode;
        private SortDirection direction = SortDirection.ASC;
        private String field;             // Or alias-based format: "emp.emp_name"
        
        public Sort(String objectCode, String fieldCode) {
            this.objectCode = objectCode;
            this.fieldCode = fieldCode;
        }
    }
}
