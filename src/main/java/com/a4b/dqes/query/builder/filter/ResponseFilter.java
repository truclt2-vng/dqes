/**
 * Created: Jan 26, 2026 2:57:38 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder.filter;

import java.util.Map;

import lombok.Data;

@Data
public class ResponseFilter {
    private String sql;
    private Map<String, Object> parameters;
}
