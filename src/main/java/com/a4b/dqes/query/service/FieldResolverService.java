package com.a4b.dqes.query.service;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.RelationPath;
import com.a4b.dqes.query.model.ResolvedField;
import com.a4b.dqes.repository.jpa.FieldMetaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for resolving field references to actual metadata
 * Optimized with batch loading for better performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FieldResolverService {
    
    private final RelationGraphService relationGraphService;
    
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
        
        // Pre-load all object metadata
        Map<String, ObjectMeta> objectMetaMap = new HashMap<>();
        for (String objectCode : requiredObjects) {
            ObjectMeta objectMeta = allObjectMetaMap.get(objectCode);
            objectMetaMap.put(objectCode, objectMeta);
        }
        
        // Pre-load all field metadata for each object
        Map<String, List<FieldMeta>> fieldMetaByObject = new HashMap<>();
        for (String objectCode : requiredObjects) {
            List<FieldMeta> fields = allObjectMetaMap.get(objectCode).getFieldMetas();
            fieldMetaByObject.put(objectCode, fields);
        }
        
        // Pre-load relation paths for non-root objects
        for (String objectCode : requiredObjects) {
            if (!objectCode.equals(context.getRootObject()) && 
                !context.getRelationPaths().containsKey(objectCode)) {
                
                Optional<RelationPath> pathOpt = relationGraphService.findPath(
                    context.getTenantCode(),
                    context.getAppCode(),
                    context.getDbconnId(),
                    context.getRootObject(),
                    objectCode
                );
                
                if (pathOpt.isPresent()) {
                    context.getRelationPaths().put(objectCode, pathOpt.get());
                }
            }
        }
        
        // Resolve all fields
        List<ResolvedField> resolvedFields = new ArrayList<>();
        for (String fieldPath : fieldPaths) {
            ResolvedField resolved = resolveFieldWithCache(fieldPath, context, objectMetaMap, fieldMetaByObject);
            resolvedFields.add(resolved);
        }
        
        return resolvedFields;
    }
    
    /**
     * Resolve a single field path using pre-loaded metadata cache
     */
    private ResolvedField resolveFieldWithCache(
        String fieldPath, 
        QueryContext context,
        Map<String, ObjectMeta> objectMetaMap,
        Map<String, List<FieldMeta>> fieldMetaByObject
    ) {
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid field path: " + fieldPath + ". Expected format: object.field");
        }
        
        String objectCode = parts[0];
        String fieldCode = parts[1];
        
        // Get object metadata from cache
        ObjectMeta objectMeta = objectMetaMap.get(objectCode);
        if (objectMeta == null) {
            throw new IllegalArgumentException("Object not found: " + objectCode);
        }
        
        // Get field metadata from cache
        FieldMeta fieldMeta = fieldMetaByObject.getOrDefault(objectCode, Collections.emptyList())
            .stream()
            .filter(f -> f.getFieldCode().equals(fieldCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Field not found: " + objectCode + "." + fieldCode));
        
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
                    objectCode
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
}
