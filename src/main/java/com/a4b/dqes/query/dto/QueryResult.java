/**
 * Created: Jan 12, 2026 1:18:04 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class QueryResult {
    private List<Map<String, Object>> rows;
    private int rowCount;
    private String generatedSql;
    private Map<String, Object> params;    
}
