/**
 * Created: Nov 28, 2025 1:14:03 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.a4b.core.ag.ag_grid.builder.AgGridConstants;
import com.a4b.core.ag.ag_grid.builder.AgGridResponseBuilder;
import com.a4b.core.ag.ag_grid.builder.PreparedAgGridQueryBuilder;
import com.a4b.core.ag.ag_grid.filter.ColumnFilter;
import com.a4b.core.ag.ag_grid.filter.TextColumnFilter;
import com.a4b.core.ag.ag_grid.request.AgGridGetRowsRequest;
import com.a4b.core.ag.ag_grid.request.ColumnVO;
import com.a4b.core.ag.ag_grid.request.SortModel;
import com.a4b.core.ag.ag_grid.response.AgGridGetRowsResponse;
import com.a4b.core.server.enums.RecordStatus;
import com.a4b.core.server.json.JSON;
import com.a4b.lib.shared.common.Fields;
import com.a4b.lib.shared.dto.request.AgGridGetRowsRequestExt;
import com.a4b.dqes.exception.DqesRuntimeException;
import com.a4b.dqes.security.SecurityUtils;
import com.google.common.base.CaseFormat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic base service for aggregate APIs using AgGrid + SCD style.
 *
 * @param <CREATE_CMD>        Create command type
 * @param <UPDATE_CMD>        Update command type
 * @param <VIEW_DTO>          Simple view DTO (for grid)
 * @param <VIEW_EXT_DTO>      Extended view DTO (detail)
 * @param <BULK_APPROVE_CMD>  Bulk approve command type
 * @param <BULK_DELETE_CMD>   Bulk delete command type
 */
@Slf4j
public abstract class BaseAggService<
    DTO,
    CREATE_CMD,
    UPDATE_CMD,
    BULK_APPROVE_CMD,
    BULK_DELETE_CMD
> {

	protected String fromSql;
	protected String selectSql;
	protected String selectCountSql;
	protected abstract String permissionCheckExp();

    private final CommandGateway commandGateway;
    private EntityManager entityManager;
    private NamedParameterJdbcTemplate template;

	protected static final String SORT_DEF_COL = "update_date";
	protected static final String ID_COL = "id";
    private static final List<String> PROPS_COLUMN_NAMES = Arrays.asList("props", "extProps");
    private static final List<String> IGNORE_COLUMN_NAMES = Arrays.asList("ftsString", "ftsStringValue", "ftsValue", "modNo", "currentFlg", "effectiveStart", "effectiveEnd");

    protected BaseAggService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public EntityManager getEntityManager() {
		return entityManager;
	}

    @PersistenceContext
	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

    public NamedParameterJdbcTemplate getTemplate() {
        return template;
    }

    @Autowired
    public void setTemplate(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    public BaseRespDto create(CREATE_CMD cmd){
        return commandGateway.sendAndWait(cmd);
    }

    public BaseRespDto update(UPDATE_CMD cmd){
        return commandGateway.sendAndWait(cmd);
    }

    public void bulkApprove(BULK_APPROVE_CMD cmd) {
        commandGateway.sendAndWait(cmd);
    }

    public void bulkDelete(BULK_DELETE_CMD cmd) {
        commandGateway.sendAndWait(cmd);
    }

	public DTO getDetailByAggId(UUID aggId){
		AgGridGetRowsRequestExt request = new AgGridGetRowsRequestExt();
		request.setStartRow(0);
		request.setEndRow(1);
		addAggIdFilter(request, aggId);
        addTenantFilter(request, getTenantCode());
        return getDetail(request);
	}

	public DTO getDetail(AgGridGetRowsRequestExt request) {
		AgGridGetRowsResponse<DTO> rowsResponse = pivotPaging(request);
        List<DTO> data = rowsResponse.getData();
        return data.isEmpty() ? null : data.get(0);
    }

	public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request) {
		addDefaultFilter(request);
        addDefaultSort(request);
		return pivotPaging(
			request, buildSelectPaging(request.getFields()), this.fromSql, permissionCheckExp(), null, null, null
		);
	}

	public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request, String selectSql, String fromSql) {
		return pivotPaging(request, selectSql, fromSql, null, null, null, null);
	}

	public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request, String selectSql, String fromSql, String permissionCheckingExp) {
		return pivotPaging(request, selectSql, fromSql, permissionCheckingExp,null, null, null);
	}

	public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request, 
		String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql) {
		return pivotPaging(request, selectSql, fromSql, permissionCheckingExp, customWhereSql, null, null);
	}

	public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request,
			String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql, Map<String, Object> sqlParamExt) {
		return pivotPaging(request, selectSql, fromSql, permissionCheckingExp,customWhereSql, sqlParamExt, null);
	}

    public AgGridGetRowsResponse<DTO> pivotPaging(AgGridGetRowsRequestExt request,
			String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql, Map<String, Object> sqlParamExt, Map<String, String> columnAliasMap) {

		request = normalizeRequest(request);
		// first obtain the pivot values from the DB for the requested pivot columns
		Map<String, List<String>> pivotValues = new HashMap<>();
		PreparedAgGridQueryBuilder queryBuilder = new PreparedAgGridQueryBuilder().withEntityManager(getEntityManager())
				.withPropertiesColumnNames(PROPS_COLUMN_NAMES);
		;
		queryBuilder.setRemoveAccent(false);

		// build custom fts sql if enabled
		// buildCustomFtsSql(request, queryBuilder);
		
		// generate sql
		var sqlWithParams = queryBuilder.createSqlWithPermissionChecking(request, selectSql, fromSql, customWhereSql,
				pivotValues, permissionCheckingExp, columnAliasMap);

		Map<String,Object> sqlParams = new HashMap<>();
		sqlParams.putAll(sqlWithParams.getParams());
		if(sqlParamExt != null && !sqlParamExt.isEmpty()){
			sqlParams.putAll(sqlParamExt);
		}

		log.debug("Executing SQL: {} with params: {}", sqlWithParams.getSql(), JSON.writeValueAsStringNoException(sqlParams));
		// query db for rows
		List<DTO> rows = (List<DTO>) getTemplate().queryForList(sqlWithParams.getSql(), sqlParams);

		AgGridGetRowsResponse<DTO> response = AgGridResponseBuilder.createResponse(request, rows, pivotValues);
		return normalizeResponse(response);

	}

	public Long pivotCount(AgGridGetRowsRequestExt request) {
		addDefaultFilter(request);
		return pivotCount(request, this.selectCountSql , this.fromSql, permissionCheckExp(), null, null, null);
	}

	public Long pivotCount(AgGridGetRowsRequestExt request, String selectSql, String fromSql) {
		return pivotCount(request, selectSql, fromSql, null, null, null, null);
	}

	public Long pivotCount(AgGridGetRowsRequestExt request, 
		String selectSql, String fromSql, String permissionCheckingExp) {
		return pivotCount(request, selectSql, fromSql, permissionCheckingExp, null, null, null);
	}

	public Long pivotCount(AgGridGetRowsRequestExt request, 
		String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql) {
		return pivotCount(request, selectSql, fromSql, permissionCheckingExp, customWhereSql, null, null);
	}

	public Long pivotCount(AgGridGetRowsRequestExt request, 
		String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql, Map<String, Object> sqlParamExt) {
		return pivotCount(request, selectSql, fromSql, permissionCheckingExp, customWhereSql, sqlParamExt, null);
	}

	public Long pivotCount(AgGridGetRowsRequestExt request, 
		String selectSql, String fromSql, String permissionCheckingExp, String customWhereSql, Map<String, Object> sqlParamExt, Map<String, String> columnAliasMap) {
		request = normalizeRequest(request);
		PreparedAgGridQueryBuilder queryBuilder = new PreparedAgGridQueryBuilder().withEntityManager(getEntityManager()).withPropertiesColumnNames(PROPS_COLUMN_NAMES);
		// generate sql
		var sqlWithParams = queryBuilder.createSqlCountWithPermissionChecking(request, selectSql, fromSql, customWhereSql, permissionCheckingExp, columnAliasMap);

		Map<String,Object> sqlParams = new HashMap<>();
		sqlParams.putAll(sqlWithParams.getParams());
		if(sqlParamExt != null && !sqlParamExt.isEmpty()){
			sqlParams.putAll(sqlParamExt);
		}
		// query db for rows
		Number count = template.queryForObject(sqlWithParams.getSql(), sqlParams, Number.class);
		return count != null ? count.longValue() : 0l;
	}

    private AgGridGetRowsRequestExt normalizeRequest(AgGridGetRowsRequestExt request) {
		// row group columns
		List<ColumnVO> rowGroupCols = request.getRowGroupCols().stream()
				.map(col -> {
					col.setField(getDBColumnName(col.getField()));
					return col;
				}).collect(toList());

		// value columns
		List<ColumnVO> valueCols = request.getValueCols().stream()
				.map(col -> {
					col.setField(getDBColumnName(col.getField()));
					return col;
				}).collect(toList());

		// pivot columns
		List<ColumnVO> pivotCols = request.getPivotCols().stream()
				.map(col -> {
					col.setField(getDBColumnName(col.getField()));
					return col;
				}).collect(toList());

		List<String> groupKeys = request.getGroupKeys();

		// if filtering, what the filter model is
		Map<String, ColumnFilter> filterModel = new HashMap<>();
		for (Entry<String, ColumnFilter> entry : request.getFilterModel().entrySet()) {
            String columnName = getDBColumnName(entry.getKey());
            filterModel.put(columnName, entry.getValue());
		}

		// if sorting, what the sort model is
		List<SortModel> sortModel = request.getSortModel().stream()
                .map(md -> {
					md.setColId(getDBColumnName(md.getColId()));
					return md;
				}).collect(toList());

		request.setRowGroupCols(rowGroupCols);
		request.setValueCols(valueCols);
		request.setPivotCols(pivotCols);
		request.setGroupKeys(groupKeys);
		request.setFilterModel(filterModel);
		request.setSortModel(sortModel);

		return request;
	}

    @SuppressWarnings("unchecked")
	private <T> AgGridGetRowsResponse<T> normalizeResponse(AgGridGetRowsResponse<?> response) {
		List<String> secondaryColumnFields = response.getSecondaryColumnFields()
				.stream()
				.map(col -> col)
				.collect(toList());

		List<?> rawData = response.getData();
		List<T> newData = (List<T>) normalizeData((List<Map<String, Object>>) rawData);

		AgGridGetRowsResponse<T> typedResponse = new AgGridGetRowsResponse<>();
		typedResponse.setSecondaryColumns(secondaryColumnFields);
		typedResponse.setData(newData);
		typedResponse.setLastRow(response.getLastRow());
		return typedResponse;
	}

    @SuppressWarnings("unchecked")
	private <T> List<T> normalizeData(List<Map<String, Object>> data) {
		if (data == null)
			return null;

		List<T> newData = new ArrayList<>(data.size());

		for (Map<String, Object> item : data) {
			Map<String, Object> dt = new HashMap<>();
			for (Entry<String, Object> entry : item.entrySet()) {
				String fieldName = getFieldName(entry.getKey());
				if (IGNORE_COLUMN_NAMES.contains(fieldName))
					continue;

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

    private String getDBColumnName(String name) {
		return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
	}

	private String getFieldName(String columnName) {
		return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, columnName);
	}

	protected void addDefaultFilter(AgGridGetRowsRequest request) {
		if (request.getFilterModel() == null) {
			request.setFilterModel(new HashMap<>());
		}

		addTenantFilter(request, getTenantCode());
		addExcludeDeleteFilter(request);
	}

	protected void addDefaultSort(AgGridGetRowsRequest request) {
		if (request.getSortModel() == null || request.getSortModel().isEmpty()) {
			request.setSortModel(Arrays.asList(new SortModel(SORT_DEF_COL, "desc"),new SortModel(ID_COL, "desc")));
		}else{
			// ensure ID_COL is always the last sort to maintain consistent ordering
			boolean hasIdSort = request.getSortModel().stream()
					.anyMatch(sm -> ID_COL.equals(sm.getColId()));
			if (!hasIdSort) {
				request.getSortModel().add(new SortModel(ID_COL, "desc"));
			}
		}
	}

	protected void addTenantFilter(AgGridGetRowsRequest request, String value) {
		if (request.getFilterModel() == null) {
			request.setFilterModel(new HashMap<>());
		}
		ColumnFilter filter = new TextColumnFilter(AgGridConstants.OP_EQUALS, value);
		request.getFilterModel().put(Fields.tenantCode, filter);
	}

	protected void addExcludeDeleteFilter(AgGridGetRowsRequest request) {
		if (request.getFilterModel() == null) {
			request.setFilterModel(new HashMap<>());
		}
		ColumnFilter filter = new TextColumnFilter(AgGridConstants.OP_NOT_EQUAL, RecordStatus.D.name());
		request.getFilterModel().put(Fields.recordStatus, filter);
	}

	protected void addAggIdFilter(AgGridGetRowsRequest request, UUID value) {
		if (request.getFilterModel() == null) {
			request.setFilterModel(new HashMap<>());
		}
		ColumnFilter filter = new TextColumnFilter(AgGridConstants.OP_EQUALS, value.toString());
		request.getFilterModel().put(Fields.aggId, filter);
	}

	public String buildSelectPaging(List<String> fields) {
		return buildSelectPaging(fields, null);
	}

	public String buildSelectPaging(List<String> fields, String alias) {
		String tableAlias = (alias == null || alias.isBlank()) ? "t" : alias;

		if (fields == null || fields.isEmpty()) {
			return "select " + tableAlias + ".*";
		}

		String columns = fields.stream()
				.map(f -> tableAlias + "." + getDBColumnName(f))
				.collect(Collectors.joining(", "));

		return "select " + columns;
	}

	public String getTenantCode() {
		return SecurityUtils.getCurrentUserTenantCode();
	}

	public String getAppCode() {
		return SecurityUtils.getCurrentAppCode();
	}

	public String getUserName() {
		return SecurityUtils.getCurrentUserName();
	}
}
