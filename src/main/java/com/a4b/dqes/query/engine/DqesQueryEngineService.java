package com.a4b.dqes.query.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.a4b.dqes.query.dto.FilterSpec;
import com.a4b.dqes.query.dto.QueryRequest;
import com.a4b.dqes.query.meta.FieldMappingType;
import com.a4b.dqes.query.meta.FieldMeta;
import com.a4b.dqes.query.meta.ObjectMeta;
import com.a4b.dqes.query.meta.OperationMeta;
import com.a4b.dqes.query.meta.RelationInfo;
import com.a4b.dqes.query.meta.RelationJoinKey;
import com.a4b.dqes.query.planner.ExistsStep;
import com.a4b.dqes.query.planner.Graph;
import com.a4b.dqes.query.planner.JoinJoinStep;
import com.a4b.dqes.query.planner.JoinPathPlanner;
import com.a4b.dqes.query.planner.JoinPlan;
import com.a4b.dqes.query.planner.JoinStep;
import com.a4b.dqes.query.planner.PlanRequest;
import com.a4b.dqes.query.store.DqesMetadataStore;
import com.a4b.dqes.query.store.DqesMetadataStore.FieldKey;
import com.a4b.dqes.query.util.DotPath;
import com.a4b.dqes.query.util.Ident;
import com.google.common.base.CaseFormat;

public final class DqesQueryEngineService {

    private final DqesMetadataStore store;

    public DqesQueryEngineService(DqesMetadataStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public static String normObjectCode(String s) {
        // return s;
        return s == null ? null : getDBColumnName(s.trim()).toUpperCase(Locale.ROOT);
    }

    private static String getDBColumnName(String name) {
		return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
	}

    public QueryBuildResult buildSql(QueryRequest req) {
        req.validateBasic();
        if (req.filters != null) req.filters.forEach(FilterSpec::validateBasic);

        final String tenant = req.tenantCode;
        final String app = req.appCode;
        final int dbconnId = req.dbconnId;

        final String rootObj = normObjectCode(req.rootObject);

        // Parse dot-paths
        List<DotPath.Parsed> selectParsed = req.selectFields.stream().map(DotPath::parse).toList();

        List<FilterParsed> filtersParsed = (req.filters == null ? List.<FilterParsed>of() : req.filters.stream().map(f -> {
            DotPath.Parsed p = DotPath.parse(f.field);
            return new FilterParsed(normObjectCode(p.object()), normObjectCode(p.field()), f.operatorCode, f.value);
        }).toList());

        // Objects
        Set<String> selectObjects = selectParsed.stream().map(p -> normObjectCode(p.object())).collect(Collectors.toSet());
        Set<String> filterObjects = filtersParsed.stream().map(FilterParsed::objectCode).collect(Collectors.toSet());

        Set<String> allObjects = new HashSet<>();
        allObjects.add(rootObj);
        allObjects.addAll(selectObjects);
        allObjects.addAll(filterObjects);

        Set<String> requiredObjects = new HashSet<>(selectObjects);
        requiredObjects.add(rootObj);

        Set<String> filterOnlyObjects = new HashSet<>(filterObjects);
        filterOnlyObjects.removeAll(requiredObjects);

        // Fields
        Set<FieldKey> fieldKeys = new HashSet<>();
        for (DotPath.Parsed p : selectParsed) fieldKeys.add(new FieldKey(normObjectCode(p.object()), normObjectCode(p.field())));
        for (FilterParsed f : filtersParsed) fieldKeys.add(new FieldKey(f.objectCode(), f.fieldCode()));

        // Operators
        Set<String> opCodes = filtersParsed.stream().map(FilterParsed::operatorCode).collect(Collectors.toSet());

        // Load metadata
        Map<String, ObjectMeta> objMeta = store.loadObjectMeta(tenant, app, dbconnId, allObjects);
        // List<String> objectCodes = objMeta.keySet().stream().sorted().toList();
        List<RelationInfo> relations = store.loadRelations(tenant, app, dbconnId);
        Graph graph = new Graph(relations);

        Set<Integer> relIds = relations.stream().map(RelationInfo::id).collect(Collectors.toSet());
        Map<Integer, List<RelationJoinKey>> joinKeys = store.loadJoinKeysByRelationIds(dbconnId, relIds);

        Map<String, FieldMeta> fieldMeta = store.loadFieldMeta(tenant, app, dbconnId, fieldKeys);
        // Map<String, List<FieldMeta>> fieldMetaObj = store.loadFieldMeta(tenant, app, dbconnId, objectCodes);
        Map<String, OperationMeta> opMeta = store.loadOperationMeta(tenant, app, opCodes);

        // Validate select fields
        for (DotPath.Parsed p : selectParsed) {
            String o = normObjectCode(p.object());
            String f = normObjectCode(p.field());
            FieldMeta fm = mustField(fieldMeta, o, f);
            if (!fm.allowSelect()) throw new IllegalArgumentException("Field not allowed in SELECT: " + o + "." + p.field());
            if (fm.mappingType() != FieldMappingType.COLUMN) {
                throw new UnsupportedOperationException("Only COLUMN mapping implemented: " + o + "." + p.field());
            }
        }

        // Validate filters (dtype-op)
        for (FilterParsed f : filtersParsed) {
            FieldMeta fm = mustField(fieldMeta, f.objectCode(), f.fieldCode());
            if (!fm.allowFilter()) throw new IllegalArgumentException("Field not allowed in FILTER: " + f.objectCode() + "." + f.fieldCode());

            OperationMeta om = opMeta.get(f.operatorCode());
            if (om == null) throw new IllegalArgumentException("Unknown operatorCode: " + f.operatorCode());

            if (!store.isOperatorAllowedForDataType(tenant, app, fm.dataTypeCode(), om.code())) {
                throw new IllegalArgumentException("Operator " + om.code() + " not allowed for dataType=" + fm.dataTypeCode()
                        + " field=" + f.objectCode() + "." + f.fieldCode());
            }
        }

        // Plan joins
        PlanRequest planReq = new PlanRequest(rootObj, requiredObjects, filterOnlyObjects);
        JoinPlan plan = new JoinPathPlanner().plan(planReq, graph, objMeta, joinKeys);

        // SELECT
        StringBuilder selectSql = new StringBuilder("SELECT\n");
        List<String> selectExprs = new ArrayList<>();
        for (DotPath.Parsed p : selectParsed) {
            String obj = normObjectCode(p.object());
            String f = normObjectCode(p.field());
            FieldMeta fm = mustField(fieldMeta, obj, f);
            String alias = plan.aliasOf(obj);
            if (alias == null) throw new IllegalStateException("Missing alias for select object: " + obj);
            String expr = Ident.col(alias, fm.columnName());
            String outAlias = Ident.quoteIdentifier(obj.toLowerCase(Locale.ROOT) + "_" + p.field());
            // selectExprs.add("  " + expr + " AS " + outAlias);
            selectExprs.add("  " + expr + " AS " + fm.aliasHint());

        }
        selectSql.append(String.join(",\n", selectExprs)).append("\n");

        // FROM + JOIN
        StringBuilder fromSql = new StringBuilder();
        ObjectMeta rootMeta = objMeta.get(rootObj);
        if (rootMeta == null) throw new IllegalStateException("Missing object_meta for root " + rootObj);

        fromSql.append("FROM ")
                .append(Ident.quoteSchemaTable(rootMeta.dbTable()))
                .append(" ")
                .append(plan.rootAlias())
                .append("\n");

        List<ExistsStep> existsSteps = new ArrayList<>();
        for (JoinStep s : plan.steps()) {
            if (s instanceof JoinJoinStep j) {
                fromSql.append(j.joinType().name())
                        .append(" JOIN ")
                        .append(Ident.quoteSchemaTable(j.toTable()))
                        .append(" ")
                        .append(j.toAlias())
                        .append(" ON ")
                        .append(j.onClauseSql())
                        .append("\n");
            } else if (s instanceof ExistsStep ex) {
                existsSteps.add(ex);
            }
        }

        // Filters -> WHERE or EXISTS
        Map<String, List<String>> existsFilters = new HashMap<>();
        List<String> whereConds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        int pIndex = 0;
        for (FilterParsed f : filtersParsed) {
            FieldMeta fm = mustField(fieldMeta, f.objectCode(), f.fieldCode());
            OperationMeta om = opMeta.get(f.operatorCode());
            String opSym = (om.opSymbol() != null && !om.opSymbol().isBlank()) ? om.opSymbol() : deriveSymbol(om.code());

            String paramName = "p_" + f.objectCode().toLowerCase(Locale.ROOT) + "_" + f.fieldCode() + "_" + om.code().toLowerCase(Locale.ROOT) + "_" + pIndex++;

            Optional<ExistsStep> exOpt = plan.existsForTarget(f.objectCode());
            if (exOpt.isPresent()) {
                ExistsStep ex = exOpt.get();
                String cond = Ident.col(ex.targetAlias(), fm.columnName()) + " " + opSym + " :" + paramName;
                existsFilters.computeIfAbsent(f.objectCode(), k -> new ArrayList<>()).add(cond);
                params.put(paramName, f.value());
                continue;
            }

            String alias = plan.aliasOf(f.objectCode());
            if (alias == null) throw new IllegalStateException("Missing alias for filter object: " + f.objectCode());

            whereConds.add(Ident.col(alias, fm.columnName()) + " " + opSym + " :" + paramName);
            params.put(paramName, f.value());
        }

        for (ExistsStep ex : existsSteps) {
            List<String> exConds = existsFilters.getOrDefault(ex.targetObject(), List.of());
            String injected = exConds.isEmpty() ? "" : (" AND " + String.join(" AND ", exConds));
            whereConds.add(ex.existsSql().replace(ExistsStep.FILTER_TOKEN, injected));
        }

        StringBuilder whereSql = new StringBuilder("WHERE 1=1\n");
        for (String c : whereConds) whereSql.append("  AND ").append(c).append("\n");

        params.put("offset", req.offset);
        params.put("limit", req.limit);

        String sql = selectSql + fromSql.toString() + whereSql + "OFFSET :offset\nLIMIT :limit";
        return new QueryBuildResult(sql, params);
    }

    private static String deriveSymbol(String opCode) {
        return switch (opCode) {
            case "EQ" -> "=";
            case "NE" -> "!=";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LT" -> "<";
            case "LE" -> "<=";
            case "LIKE" -> "LIKE";
            case "ILIKE" -> "ILIKE";
            case "IN" -> "IN";
            case "NOT_IN" -> "NOT IN";
            case "BETWEEN" -> "BETWEEN";
            case "IS_NULL" -> "IS NULL";
            case "IS_NOT_NULL" -> "IS NOT NULL";
            default -> throw new IllegalArgumentException("Cannot derive symbol for op=" + opCode + " (fill op_symbol in metadata)");
        };
    }

    private static FieldMeta mustField(Map<String, FieldMeta> map, String obj, String field) {
        FieldMeta fm = map.get(obj + "." + field);
        if (fm == null) throw new IllegalArgumentException("Missing field_meta for " + obj + "." + field);
        return fm;
    }

    private record FilterParsed(String objectCode, String fieldCode, String operatorCode, Object value) {}
}
