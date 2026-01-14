package com.a4b.dqes.query.model;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of alias resolution containing the actual object code and field code
 * resolved from user-provided aliases (alias_hint or join_alias)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliasResolution {
    
    /**
     * User-provided alias (could be alias_hint from object_meta or join_alias from relation_info)
     */
    private String userAlias;
    
    /**
     * Actual object code from metadata
     */
    private String objectCode;
    
    /**
     * Actual field code from metadata
     */
    private String fieldCode;
    
    /**
     * Field metadata
     */
    private FieldMeta fieldMeta;
    
    /**
     * Object metadata
     */
    private ObjectMeta objectMeta;
    
    /**
     * Join alias from relation_info (if this field comes from a related object)
     */
    private String joinAlias;
    
    /**
     * Relation code (if this field comes from a related object)
     */
    private String relationCode;
    
    /**
     * Relation metadata (if available)
     */
    private RelationInfo relationInfo;
    
    /**
     * Whether this is the root object
     */
    private boolean isRootObject;
    
    /**
     * Get a unique key for this alias resolution
     * Used to track multiple joins to the same object with different aliases
     */
    public String getUniqueKey() {
        if (joinAlias != null) {
            return objectCode + ":" + joinAlias;
        }
        return objectCode;
    }
}
