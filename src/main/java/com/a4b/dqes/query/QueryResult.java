package com.a4b.dqes.query;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Result DTO for dynamic query execution
 */
@Data
public class QueryResult {
    private List<Map<String, Object>> rows;
    private int rowCount;
    // private String generatedSql;
    // private Map<String, String> aliasMap;
}
