/**
 * Created: Jul 14, 2025 12:31:32 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.util;

import java.beans.PropertyDescriptor;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import com.a4b.core.server.json.JSON;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonBeanPropertyRowMapper<T> extends BeanPropertyRowMapper<T> {

    public JsonBeanPropertyRowMapper(Class<T> mappedClass) {
        super(mappedClass);
    }

    @Override
    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {

        Class<?> propertyType = pd.getPropertyType();
        Object value = rs.getObject(index);

        if (propertyType == JsonNode.class) {
            if (value instanceof PGobject pg && pg.getValue() != null) {
                try {
                    return JSON.getObjectMapper().readTree(pg.getValue());
                } catch (Exception e) {
                    throw new SQLException("Failed to map jsonb to JsonNode", e);
                }
            }
            return null;
        }
        if (propertyType == String.class && value instanceof PGobject pg && "ltree".equalsIgnoreCase(pg.getType())) {
            return pg.getValue();
        }
        return super.getColumnValue(rs, index, pd);
    }
}
