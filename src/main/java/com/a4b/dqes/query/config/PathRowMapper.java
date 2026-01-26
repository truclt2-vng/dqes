/**
 * Created: Jan 16, 2026 1:26:05 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.config;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.query.model.PathRow;

public class PathRowMapper implements RowMapper<PathRow> {
    public static final PathRowMapper INSTANCE = new PathRowMapper();

    @Override
    public PathRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            String json = rs.getString("path_relation_codes");
            return new PathRow(
                    rs.getString("rel_code"),
                    rs.getString("from_object_code"),
                    rs.getString("to_object_code"),
                    rs.getString("join_alias"),
                    rs.getInt("hop_count"),
                    rs.getInt("total_weight"),
                    json == null ? null : JSON.getObjectMapper().readTree(json)
            );
        } catch (Exception e) {
            throw new SQLException("Failed to parse path_relation_codes json", e);
        }
    }
}
