/**
 * Created: Jan 26, 2026 3:10:48 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder.filter;

public record FilterSpec(String filterColumn, String operator, String dataType, Object value, Object value2) {
}
