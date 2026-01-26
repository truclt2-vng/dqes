package com.a4b.dqes.query.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.a4b.core.server.json.JSON;
import com.a4b.core.server.utils.Pair;
import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.query.config.PathRowMapper;
import com.a4b.dqes.query.model.PathRow;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.RelationPath;
import com.a4b.dqes.query.model.ResolvedField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for resolving field references to actual metadata
 * Optimized with batch loading for better performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FieldResolverService {
    
    private final RelationGraphService relationGraphService;
    private final NamedParameterJdbcTemplate dqesJdbc;
    
    /**
     * Batch resolve multiple fields for improved performance
     * Reduces N+1 query problem by pre-loading all required metadata
     */
    public List<ResolvedField> batchResolveFields(List<String> fieldPaths, QueryContext context,Map<String, ObjectMeta> allObjectMetaMap) {
        if (fieldPaths == null || fieldPaths.isEmpty()) {
            return Collections.emptyList();
        }
        // Extract unique object codes from field paths
        Set<String> requiredObjects = fieldPaths.stream()
            .map(path -> path.split("\\.")[0])
            .collect(Collectors.toSet());
        
        Set<String> pathRequiredObjects = new HashSet<>();
        pathRequiredObjects.addAll(requiredObjects);
        // Pre-load all object metadata
        Map<String, ObjectMeta> requiredObjectMetaMap = new HashMap<>();
        Map<String, List<PathRow>> bestPathByTargetMap = new HashMap<>();
        for (String objectCode : requiredObjects) {
            ObjectMeta objectMeta = allObjectMetaMap.get(objectCode);
            if(objectMeta == null){
                RelationInfo relationInfo = getRelationInfo(context, context.getRootObject(), objectCode);
                if(relationInfo != null){
                    ObjectMeta objectMetaRel = allObjectMetaMap.get(relationInfo.getToObjectCode());
                    requiredObjectMetaMap.put(objectCode, objectMetaRel);
                }else{
                    // BFS search for best paths
                    List<PathRow> bestPathRows = findBestPaths(context.getDbconnId(), context.getRootObject(),6);
                    if(bestPathRows == null || bestPathRows.isEmpty()){
                        throw new IllegalArgumentException("Object not found: " + objectCode);
                    }
                    List<PathRow> bestPathByTarget = bestPathByTarget(bestPathRows, objectCode);
                    if (bestPathByTarget == null || bestPathByTarget.isEmpty()) {
                        throw new IllegalArgumentException("Object not found: " + objectCode);
                    }
                    bestPathByTargetMap.put(objectCode, bestPathByTarget);
                    bestPathByTarget.forEach(row -> {
                        ObjectMeta objectMetaRel = allObjectMetaMap.get(row.toObjectCode());
                        requiredObjectMetaMap.put(row.joinAlias(), objectMetaRel);
                        pathRequiredObjects.add(row.joinAlias());
                    });
                }
                
            }else{
                requiredObjectMetaMap.put(objectCode, objectMeta);
            }
            
        }
        
        // Pre-load all field metadata for each object
        Map<String, List<FieldMeta>> fieldMetaByObject = new HashMap<>();
        for (String objectCode : pathRequiredObjects) {
            List<FieldMeta> fields = requiredObjectMetaMap.get(objectCode).getFieldMetas();
            fieldMetaByObject.put(objectCode, fields);
        }
        
        // Pre-load relation paths for non-root objects
        for (String objectCode : pathRequiredObjects) {
            if (!objectCode.equals(context.getRootObject()) && 
                !context.getRelationPaths().containsKey(objectCode)) {

                String fromObject = context.getRootObject();
                List<PathRow> bestPathByTarget = bestPathByTargetMap.get(objectCode);
                Pair<String, String> fromObjectAndAlias = resolveFromObject(context, bestPathByTarget, fromObject, objectCode);

                ObjectMeta objectMetaRel = requiredObjectMetaMap.get(objectCode);
                
                Optional<RelationPath> pathOpt = relationGraphService.findPath(
                    context.getTenantCode(),
                    context.getAppCode(),
                    context.getDbconnId(),
                    fromObjectAndAlias.getFirst(),
                    fromObjectAndAlias.getSecond(),
                    objectCode,
                    objectMetaRel
                );
                
                if (pathOpt.isPresent()) {
                    context.getRelationPaths().put(objectCode, pathOpt.get());
                }
            }
        }
        
        // Resolve all fields
        List<ResolvedField> resolvedFields = new ArrayList<>();
        // requiredObjectMetaMap.forEach((key, value) -> {
        //     String fieldPath = getFielPath(fieldPaths, key);
        //     if(fieldPath != null){
        //         String[] parts = fieldPath.split("\\.", 2);
        //         if (parts.length != 2) {
        //             throw new IllegalArgumentException("Invalid field path: " + fieldPath + ". Expected format: object.field");
        //         }
        //         String objectCode = parts[0]; 
        //         String fieldCode = parts[1];

        //         ResolvedField resolved = resolveFieldWithCache(objectCode, fieldCode, context, requiredObjectMetaMap, fieldMetaByObject);
        //         resolvedFields.add(resolved);
        //     }else{
        //         ResolvedField resolved = resolveFieldWithCache(key, null, context, requiredObjectMetaMap, fieldMetaByObject);
        //         resolvedFields.add(resolved);
        //     }
        // });

        for (String fieldPath : fieldPaths) {
            String[] parts = fieldPath.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid field path: " + fieldPath + ". Expected format: object.field");
            }
            String objectCode = parts[0]; 
            String fieldCode = parts[1];

            List<PathRow> bestPathByTarget = bestPathByTargetMap.get(objectCode);
            if(bestPathByTarget != null && !bestPathByTarget.isEmpty()){
                bestPathByTarget.forEach(row -> {
                    String adjustedObjectCode = row.joinAlias();
                    String adjustedFieldCode = null;
                    if(objectCode .equals(adjustedObjectCode)){
                        adjustedFieldCode = fieldCode;
                    }
                    ResolvedField resolved = resolveFieldWithCache(adjustedObjectCode, adjustedFieldCode, context, requiredObjectMetaMap, fieldMetaByObject);
                    resolved.setSeq(row.hopCount());
                    resolvedFields.add(resolved);
                });
            }else{
                ResolvedField resolved = resolveFieldWithCache(objectCode, fieldCode, context, requiredObjectMetaMap, fieldMetaByObject);
                resolvedFields.add(resolved);
            }
        }
        
        return resolvedFields;
    }
    
    /**
     * Resolve a single field path using pre-loaded metadata cache
     */
    private ResolvedField resolveFieldWithCache(
        String objectCode, 
        String fieldCode, 
        QueryContext context,
        Map<String, ObjectMeta> objectMetaMap,
        Map<String, List<FieldMeta>> fieldMetaByObject
    ) {
        
        // Get object metadata from cache
        ObjectMeta objectMeta = objectMetaMap.get(objectCode);
        if (objectMeta == null) {
            throw new IllegalArgumentException("Object not found: " + objectCode);
        }

        // Get relation path if not the root object
        RelationPath relationPath = null;
        if (!objectCode.equals(context.getRootObject())) {
            relationPath = context.getRelationPaths().get(objectCode);
            if (relationPath == null) {
                throw new IllegalArgumentException(
                    "No path found from root object " + context.getRootObject() + " to " + objectCode
                );
            }
        }
        // Get or generate alias for the object
        String runtimeAlias = context.getOrGenerateAlias(objectCode, objectMeta.getAliasHint());
        if(fieldCode == null){
            return ResolvedField.builder()
            .originalFieldPath(objectCode)
            .objectCode(objectCode)
            .dbTable(objectMeta.getDbTable())
            .objectAlias(objectMeta.getAliasHint())
            .runtimeAlias(runtimeAlias)
            .relationPath(relationPath)
            .build();
        }
        // Get field metadata from cache
        FieldMeta fieldMeta = fieldMetaByObject.getOrDefault(objectCode, Collections.emptyList())
            .stream()
            .filter(f -> f.getFieldCode().equals(fieldCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Field not found: " + objectCode + "." + fieldCode));
        
        
        
        return ResolvedField.builder()
            .originalFieldPath(objectCode + "." + fieldCode)
            .objectCode(objectCode)
            .dbTable(objectMeta.getDbTable())
            .objectAlias(objectMeta.getAliasHint())
            .fieldCode(fieldCode)
            .columnName(fieldMeta.getColumnName())
            .dataType(fieldMeta.getDataType())
            .aliasHint(fieldMeta.getAliasHint())
            .runtimeAlias(runtimeAlias)
            .relationPath(relationPath)
            .mappingType(fieldMeta.getMappingType())
            .selectExprCode(fieldMeta.getSelectExprCode())
            .filterExprCode(fieldMeta.getFilterExprCode())
            .build();
    }
    
    /**
     * Resolve a field path like "employee.employeeCode" or "gender.name"
     * to actual metadata and determine the join path if needed
     * NOTE: For single field resolution, prefer batchResolveFields for better performance
     */
    public ResolvedField resolveField(String fieldPath, QueryContext context) {
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid field path: " + fieldPath + ". Expected format: object.field");
        }
        
        String objectCode = parts[0];
        String fieldCode = parts[1];
        
        // Get object metadata
        ObjectMeta objectMeta = context.getAllObjectMetaMap().get(objectCode);
        
        // Get field metadata
        FieldMeta fieldMeta = getFieldMeta(context, objectCode, fieldCode);
        if (fieldMeta == null) {
            throw new IllegalArgumentException(
                "Field not found: " + objectCode + "." + fieldCode
            );
        }
        
        // Resolve relation path if not the root object
        RelationPath relationPath = null;
        if (!objectCode.equals(context.getRootObject())) {
            relationPath = context.getRelationPaths().get(objectCode);
            if (relationPath == null) {
                // Find path from root to this object
                Optional<RelationPath> pathOpt = relationGraphService.findPath(
                    context.getTenantCode(),
                    context.getAppCode(),
                    context.getDbconnId(),
                    context.getRootObject(),
                    objectCode,
                    null,
                    null // Change:JOINALIAS TODO
                );
                
                if (pathOpt.isEmpty()) {
                    throw new IllegalArgumentException(
                        "No path found from root object " + context.getRootObject() + 
                        " to " + objectCode
                    );
                }
                
                relationPath = pathOpt.get();
                context.getRelationPaths().put(objectCode, relationPath);
            }
        }
        
        // Get or generate alias for the object
        String runtimeAlias = context.getOrGenerateAlias(objectCode, objectMeta.getAliasHint());
        
        return ResolvedField.builder()
            .originalFieldPath(fieldPath)
            .objectCode(objectCode)
            .dbTable(objectMeta.getDbTable())
            .objectAlias(objectMeta.getAliasHint())
            .fieldCode(fieldCode)
            .columnName(fieldMeta.getColumnName())
            .dataType(fieldMeta.getDataType())
            .aliasHint(fieldMeta.getAliasHint())
            .runtimeAlias(runtimeAlias)
            .relationPath(relationPath)
            .mappingType(fieldMeta.getMappingType())
            .selectExprCode(fieldMeta.getSelectExprCode())
            .filterExprCode(fieldMeta.getFilterExprCode())
            .build();
    }

    private FieldMeta getFieldMeta(QueryContext context, String objectCode, String fieldCode) {
        ObjectMeta objectMeta = context.getAllObjectMetaMap().get(objectCode);
        return objectMeta.getFieldMetas().stream()
            .filter(f -> f.getFieldCode().equals(fieldCode))
            .findFirst()
            .orElse(null);
    }

    private RelationInfo getRelationInfo(QueryContext context, String fromObject, String toObject) {
        List<RelationInfo> relations = context.getAllRelationInfos().stream()
            .filter(r -> r.getFromObjectCode().equals(fromObject) && r.getJoinAlias().equals(toObject))
            .collect(Collectors.toList());
        if (relations.isEmpty()) {
            // throw new IllegalArgumentException("No relation found from " + fromObject + " to " + toObject);
            return null;
        }
        return relations.get(0); // Assuming single relation for simplicity
    }

    public List<PathRow> findBestPaths(
            long connId,
            String fromObjectCode,
            Integer maxDepth // nullable -> default in DB
    ) {
        String sql = """
            SELECT
                rel_code,
                from_object_code,
                to_object_code,
                join_alias,
                hop_count,
                total_weight,
                path_relation_codes
            FROM dqes.fn_best_paths_from_object(:connId, :fromObjectCode, :maxDepth)
            order by hop_count asc
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("connId", connId)
                .addValue("fromObjectCode", fromObjectCode)
                .addValue("maxDepth", maxDepth);

        return dqesJdbc.query(sql, params, PathRowMapper.INSTANCE);
    }

    private List<PathRow> bestPathByTarget(List<PathRow> pathFromRoots, String tagetObject){
        TypeReference<List<String>> listStringTypeRef = new TypeReference<>() {};
        Set<String> requiredRelationCodes = new HashSet<>();
        for(PathRow row : pathFromRoots){
            if(row.joinAlias().equals(tagetObject)){
                JsonNode pathRelationCodesJson = row.pathRelationCodesJson();
                List<String> relationCodes = JSON.getObjectMapper().convertValue(pathRelationCodesJson, listStringTypeRef);
                requiredRelationCodes.addAll(relationCodes);
            }
        }
        return pathFromRoots.stream()
            .filter(p -> requiredRelationCodes.contains(p.relCode()))
            .collect(Collectors.toList());
    }

    private Pair<String, String> resolveFromObject(QueryContext context, List<PathRow> bestPathByTarget, String fromObject, String targetObject){
        if(bestPathByTarget == null || bestPathByTarget.isEmpty()){
            return new Pair<>(fromObject, null);
        }

        String fromAlias = null;
        List<RelationInfo> allRelations = context.getAllRelationInfos();
        for(PathRow row : bestPathByTarget){
            String relCode = row.relCode();
            RelationInfo relationInfo = allRelations.stream()
                .filter(r -> r.getCode().equals(relCode))
                .findFirst()
                .orElse(null);
            if(relationInfo != null){
                fromObject = relationInfo.getToObjectCode();
                fromAlias = relationInfo.getJoinAlias();
                return new Pair<>(fromObject, fromAlias);
            }
        }
        return new Pair<>(fromObject, null);
    }
}
