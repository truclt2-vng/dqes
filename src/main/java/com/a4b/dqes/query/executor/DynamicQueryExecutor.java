/**
 * Created: Jan 12, 2026 1:20:45 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.datasource.DynamicDataSourceService;
import com.a4b.dqes.exception.DqesRuntimeException;
import com.a4b.dqes.query.dto.QueryRequest;
import com.a4b.dqes.query.dto.QueryResult;
import com.a4b.dqes.query.engine.DqesQueryEngineService;
import com.a4b.dqes.query.engine.QueryBuildResult;
import com.a4b.dqes.query.store.JdbcDqesMetadataStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DynamicQueryExecutor {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JdbcDqesMetadataStore metadataStore;
    private final DqesQueryEngineService svc;
    private final DynamicDataSourceService dataSourceService;

    public DynamicQueryExecutor(NamedParameterJdbcTemplate jdbcTemplate,DynamicDataSourceService dataSourceService){
        this.jdbcTemplate = jdbcTemplate;
        this.dataSourceService = dataSourceService;

        this.metadataStore = new JdbcDqesMetadataStore(jdbcTemplate);
        this.svc = new DqesQueryEngineService(metadataStore);
        
    }

    public QueryResult execute(QueryRequest request) {
        QueryBuildResult generatedSql = svc.buildSql(request);

        NamedParameterJdbcTemplate targetJdbc = dataSourceService.getJdbcTemplate(
            request.tenantCode,
            request.appCode,
            request.dbconnId
        );

        List<Map<String, Object>> rows = targetJdbc.queryForList(
            generatedSql.sql(), 
            generatedSql.params()
        );

        rows = normalizeData(rows);
        
        QueryResult result = new QueryResult();
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setGeneratedSql(generatedSql.sql());
        result.setParams(generatedSql.params());
        
        log.info("Query executed successfully: {} rows returned", rows.size());
        
        return result;
    }

    private <T> List<T> normalizeData(List<Map<String, Object>> data) {
		if (data == null)
			return null;

		List<T> newData = new ArrayList<>(data.size());

		for (Map<String, Object> item : data) {
			Map<String, Object> dt = new HashMap<>();
			for (Entry<String, Object> entry : item.entrySet()) {
				String fieldName = entry.getKey();
				if (entry.getValue() instanceof PGobject val) {
					dt.put(fieldName, getPgObjectValue(val));
				} else {
					dt.put(fieldName, entry.getValue());
				}
			}
			newData.add((T) dt); // casting Map to T
		}

		return newData;
	}

	private Object getPgObjectValue(PGobject val) {
		if ("jsonb".equals(val.getType()) || "json".equals(val.getType())) {
			String json = val.getValue();
			if (json == null) {
				return null;
			}
			try {
				json = json.trim();
				if (json.startsWith("[")) {
					List lst = JSON.getObjectMapper().readValue(json, List.class);
					return lst;
				} else if (json.startsWith("{")) {
					return JSON.getObjectMapper().readValue(json, Map.class);
				}

				return json;
			} catch (Exception e) {
				throw new DqesRuntimeException("Failed to read PgObject value", e);
			}

		} else if ("ltree".equals(val.getType())) {
			return val.getValue();
		}

		return val;
	}
}
