package com.a4b.dqes.query.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.query.model.AliasResolution;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.repository.jpa.FieldMetaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for resolving user-provided aliases (alias_hint, join_alias) 
 * to actual object codes and field codes.
 * 
 * Supports:
 * - Multiple joins to the same table with different aliases
 * - Self-referential relationships
 * - User-friendly field references
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AliasResolverService {
    
    private final ObjectMetaRepository objectMetaRepository;
    private final RelationInfoRepository relationInfoRepository;
    private final FieldMetaRepository fieldMetaRepository;
    
    /**
     * Resolve a field path like "codeList.code" or "workerCategory.name"
     * where first part is alias_hint or join_alias, second part is field alias_hint
     * 
     * @param fieldPath Format: {alias}.{field_alias}
     * @param context Query context with metadata
     * @return AliasResolution containing object code, field code, and join info
     */
    public AliasResolution resolveFieldPath(String fieldPath, QueryContext context) {
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid field path: " + fieldPath + ". Expected format: {alias}.{field_alias}"
            );
        }
        
        String userAlias = parts[0];
        String fieldAlias = parts[1];
        
        log.debug("Resolving field path: {} (alias: {}, field: {})", fieldPath, userAlias, fieldAlias);
        
        // Check if this is the root object
        ObjectMeta rootObj = context.getAllObjectMetaMap().get(context.getRootObject());
        if (rootObj != null && userAlias.equals(rootObj.getAliasHint())) {
            return resolveRootField(rootObj, fieldAlias, context);
        }
        
        // Try to find relation with matching join_alias
        Optional<RelationInfo> relationByJoinAlias = relationInfoRepository
            .findByTenantCodeAndAppCodeAndJoinAlias(
                context.getTenantCode(), 
                context.getAppCode(), 
                userAlias
            );
        
        if (relationByJoinAlias.isPresent()) {
            return resolveRelationField(relationByJoinAlias.get(), fieldAlias, context);
        }
        
        // Try to find object with matching alias_hint
        Optional<ObjectMeta> objectByAlias = objectMetaRepository
            .findByTenantCodeAndAppCodeAndAliasHint(
                context.getTenantCode(),
                context.getAppCode(),
                userAlias
            );
        
        if (objectByAlias.isPresent()) {
            return resolveObjectField(objectByAlias.get(), fieldAlias, context, null);
        }
        
        throw new IllegalArgumentException(
            "Unknown alias: " + userAlias + ". Not found as join_alias or object alias_hint."
        );
    }
    
    /**
     * Resolve field for root object
     */
    private AliasResolution resolveRootField(
        ObjectMeta rootObject, 
        String fieldAlias,
        QueryContext context
    ) {
        FieldMeta field = findFieldByAlias(rootObject, fieldAlias);
        
        return AliasResolution.builder()
            .userAlias(rootObject.getAliasHint())
            .objectCode(rootObject.getObjectCode())
            .fieldCode(field.getFieldCode())
            .fieldMeta(field)
            .objectMeta(rootObject)
            .isRootObject(true)
            .build();
    }
    
    /**
     * Resolve field for a related object via specific relation (with join_alias)
     */
    private AliasResolution resolveRelationField(
        RelationInfo relation,
        String fieldAlias,
        QueryContext context
    ) {
        ObjectMeta targetObj = context.getAllObjectMetaMap().get(relation.getToObjectCode());
        if (targetObj == null) {
            throw new IllegalArgumentException(
                "Target object not found: " + relation.getToObjectCode()
            );
        }
        
        FieldMeta field = findFieldByAlias(targetObj, fieldAlias);
        
        return AliasResolution.builder()
            .userAlias(relation.getJoinAlias())
            .objectCode(targetObj.getObjectCode())
            .fieldCode(field.getFieldCode())
            .fieldMeta(field)
            .objectMeta(targetObj)
            .joinAlias(relation.getJoinAlias())
            .relationCode(relation.getCode())
            .relationInfo(relation)
            .isRootObject(false)
            .build();
    }
    
    /**
     * Resolve field for an object by its alias_hint (when no specific join_alias)
     */
    private AliasResolution resolveObjectField(
        ObjectMeta object,
        String fieldAlias,
        QueryContext context,
        String joinAlias
    ) {
        FieldMeta field = findFieldByAlias(object, fieldAlias);
        
        return AliasResolution.builder()
            .userAlias(object.getAliasHint())
            .objectCode(object.getObjectCode())
            .fieldCode(field.getFieldCode())
            .fieldMeta(field)
            .objectMeta(object)
            .joinAlias(joinAlias)
            .isRootObject(object.getObjectCode().equals(context.getRootObject()))
            .build();
    }
    
    /**
     * Find field by its alias_hint
     */
    private FieldMeta findFieldByAlias(ObjectMeta object, String fieldAlias) {
        return object.getFieldMetas().stream()
            .filter(f -> f.getAliasHint().equals(fieldAlias))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field not found with alias: " + object.getAliasHint() + "." + fieldAlias +
                ". Available fields: " + getAvailableFieldAliases(object)
            ));
    }
    
    /**
     * Get list of available field aliases for error messages
     */
    private String getAvailableFieldAliases(ObjectMeta object) {
        List<String> aliases = object.getFieldMetas().stream()
            .map(FieldMeta::getAliasHint)
            .sorted()
            .toList();
        return String.join(", ", aliases);
    }
    
    /**
     * Validate that an alias is unique within the query context
     */
    public void validateAliasUniqueness(QueryContext context) {
        // This can be enhanced to check for alias collisions
        // between different relations and objects
        log.debug("Validating alias uniqueness for query context");
    }
}
