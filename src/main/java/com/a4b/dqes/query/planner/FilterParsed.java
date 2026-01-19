/**
 * Created: Jan 19, 2026 5:09:36 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

public record FilterParsed(String objectCode, String fieldCode, String operator, Object value, Object value2) {}
