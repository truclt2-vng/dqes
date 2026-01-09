package com.a4b.dqes.query.metadata;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pre-computed shortest path between objects
 * Maps to: dqes.qrytb_object_path_cache
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObjectPathCache {
    private Integer id;
    private String tenantCode;
    private String appCode;
    private String fromObjectCode;
    private String toObjectCode;
    private Integer hopCount;
    private Integer totalWeight;
    private List<String> pathRelationCodes;     // Ordered list of relation codes
    private Integer dbconnId;
}
