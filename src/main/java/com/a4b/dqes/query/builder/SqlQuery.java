/**
 * Created: Jan 26, 2026 12:19:48 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder;

import java.util.Map;

import lombok.Data;

@Data
public class SqlQuery {
    private String sql;
    private Map<String, Object> parameters;
}
