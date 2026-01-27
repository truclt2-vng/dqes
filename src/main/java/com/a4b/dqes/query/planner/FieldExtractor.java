/**
 * Created: Jan 19, 2026 5:25:56 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.a4b.dqes.query.dto.FilterCriteria;

public class FieldExtractor {

    public static List<FieldKey> extractSelectFields(List<String> selectFields) {
       if(selectFields == null || selectFields.isEmpty()) {
           return List.of();
       }
         return selectFields.stream()
                .map(DotPath::parse)
                .toList();
    }

    public static Set<String> extractFilterFields(List<FilterCriteria> filters) {
        Set<String> result = new HashSet<>();
        if (filters == null || filters.isEmpty()) {
            return result;
        }
        for (FilterCriteria filter : filters) {
            collect(filter, result);
        }
        return result;
    }

    private static void collect(FilterCriteria filter, Set<String> result) {
        if (filter == null) {
            return;
        }

        // 1. Collect own field
        if (filter.getField() != null && !filter.getField().isBlank()) {
            result.add(filter.getField());
        }

        // 2. Collect nested filters
        if (filter.getSubFilters() != null && !filter.getSubFilters().isEmpty()) {
            for (FilterCriteria sub : filter.getSubFilters()) {
                collect(sub, result);
            }
        }
    }
}

