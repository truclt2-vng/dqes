/**
 * Created: Jul 29, 2025 1:31:22 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class BaseRespDto implements Serializable{
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique identifier")
    private Long id;

    @Schema(description = "Code")
    private String code;
    
    @Schema(description = "Aggregate ID")
    private UUID aggId;

    private Map<String , Object> additionalProperties = new HashMap<>();

    public BaseRespDto() {
    }

    public BaseRespDto(Long id) {
        this.id = id;
    }

    public BaseRespDto(Long id, String code) {
        this.id = id;
        this.code = code;
    }
    
    public BaseRespDto(Long id, String code, UUID aggId) {
        this.id = id;
        this.code = code;
        this.aggId = aggId;
    }

    public void addAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }
    
}
