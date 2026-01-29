package com.a4b.dqes.datasource;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.a4b.dqes.datasource.exception.DataOperationException;
import com.a4b.dqes.datasource.exception.DuplicateKeyException;
import com.a4b.dqes.datasource.exception.ForeignKeyViolationException;
import com.a4b.dqes.datasource.exception.NotNullViolationException;
import com.a4b.dqes.dto.schemacache.FieldMetaRC;
import com.a4b.dqes.dto.schemacache.ObjectMetaRC;
import com.a4b.dqes.web.rest.DynamicDataApi.InsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic Data Operation Service
 * 
 * Provides methods to dynamically insert, update, delete data 
 * into any table using DynamicDataSource connections.
 * Uses programmatic transaction management for target datasources.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDataOperationService {
    
    private final DynamicDataSourceService dataSourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create TransactionTemplate for target datasource
     */
    private TransactionTemplate createTransactionTemplate(String dbConnCode) {
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(
            dataSourceService.getDataSource(dbConnCode)
        );
        return new TransactionTemplate(txManager);
    }

    /**
     * Handle database exceptions and convert to meaningful custom exceptions
     */
    private void handleDatabaseException(Exception e, String tableName, String operation) {
        log.error("Database error during {} on table {}: {}", operation, tableName, e.getMessage());
        
        if (e instanceof DataIntegrityViolationException) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            
            if (rootCause instanceof PSQLException) {
                PSQLException pse = (PSQLException) rootCause;
                String sqlState = pse.getSQLState();
                String message = pse.getMessage();
                
                // 23505: unique_violation
                if ("23505".equals(sqlState)) {
                    String constraintName = extractConstraintName(message);
                    String duplicateKey = extractDuplicateKey(message);
                    throw new DuplicateKeyException(
                        "Duplicate key violation: " + duplicateKey,
                        tableName,
                        constraintName,
                        duplicateKey,
                        e
                    );
                }
                
                // 23503: foreign_key_violation
                if ("23503".equals(sqlState)) {
                    String constraintName = extractConstraintName(message);
                    String referencedTable = extractReferencedTable(message);
                    throw new ForeignKeyViolationException(
                        "Foreign key violation: Record is still referenced by " + referencedTable,
                        tableName,
                        constraintName,
                        referencedTable,
                        e
                    );
                }
                
                // 23502: not_null_violation
                if ("23502".equals(sqlState)) {
                    String columnName = extractColumnName(message);
                    throw new NotNullViolationException(
                        "Column '" + columnName + "' cannot be null",
                        tableName,
                        columnName,
                        e
                    );
                }
            }
        }
        
        // Default exception
        throw new DataOperationException("Database operation failed: " + e.getMessage(), e);
    }

    /**
     * Extract constraint name from PostgreSQL error message
     */
    private String extractConstraintName(String message) {
        Pattern pattern = Pattern.compile("constraint \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    /**
     * Extract duplicate key details from error message
     */
    private String extractDuplicateKey(String message) {
        Pattern pattern = Pattern.compile("Key \\(([^)]+)\\)=\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + "=" + matcher.group(2);
        }
        return "unknown";
    }

    /**
     * Extract referenced table from foreign key error
     */
    private String extractReferencedTable(String message) {
        Pattern pattern = Pattern.compile("table \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    /**
     * Extract column name from not null error
     */
    private String extractColumnName(String message) {
        Pattern pattern = Pattern.compile("column \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    /**
     * Convert Map/List values to PostgreSQL JSONB to avoid hstore extension requirement
     */
    private Map<String, Object> prepareData(Map<String, Object> data, Map<String, String> fieldCodeToColumnMap) {
        Map<String, Object> prepared = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            
            // Convert Map or List to JSONB
            if (value instanceof Map || value instanceof List) {
                try {
                    PGobject jsonObject = new PGobject();
                    jsonObject.setType("jsonb");
                    jsonObject.setValue(objectMapper.writeValueAsString(value));
                    prepared.put(fieldCodeToColumnMap.get(entry.getKey()), jsonObject);
                } catch (Exception e) {
                    log.warn("Failed to convert {} to JSONB, using as-is", entry.getKey(), e);
                    prepared.put(fieldCodeToColumnMap.get(entry.getKey()), value);
                }
            } else {
                prepared.put(fieldCodeToColumnMap.get(entry.getKey()), value);
            }
        }
        
        return prepared;
    }

    /**
     * Insert a single row into a table
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name (with schema if needed)
     * @param data Column name -> value map
     * @return Generated ID (if any)
     */
    public Map<String, Object> insert(InsertRequest request) {
        String dbConnCode = request.getDbConnCode();
        String objectCode = request.getObjectCode();

        List<ObjectMetaRC> objectMetas = request.getDbSchemaCacheRc().getObjectMetas();
        Map<String, ObjectMetaRC> allObjectMetaMap = objectMetas.stream()
                .collect(Collectors.toMap(ObjectMetaRC::getObjectCode, om -> om));
        ObjectMetaRC targetObjectMeta = allObjectMetaMap.values().stream()
                .filter(om -> om.getObjectCode().equalsIgnoreCase(objectCode))
                .findFirst()
                .orElse(null);
        if (targetObjectMeta == null) {
            throw new IllegalArgumentException("Object code not found in schema cache: " + request.getObjectCode());
        }

        //List Map fieldCode to columnName mapping
        List<FieldMetaRC> fieldMetas = targetObjectMeta.getFieldMetas();
        Map<String, String> fieldCodeToColumnMap = fieldMetas.stream()
                .collect(Collectors.toMap(FieldMetaRC::getFieldCode, FieldMetaRC::getColumnName));

        String tableName = targetObjectMeta.getDbTable();
        Map<String, Object> data = request.getData();
        
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be empty");
        }
        
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        try {
            return txTemplate.execute(status -> {
                NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
                
                // Prepare data (convert Map/List to JSONB)
                Map<String, Object> preparedData = prepareData(data, fieldCodeToColumnMap);
                
                // Build INSERT statement
                String columns = String.join(", ", preparedData.keySet());
                String params = preparedData.keySet().stream()
                    .map(col -> ":" + col)
                    .collect(Collectors.joining(", "));
                
                String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", 
                    tableName, columns, params);
                
                log.debug("Executing insert: {} with data: {}", sql, preparedData);
                
                // Execute with key holder to get generated ID
                KeyHolder keyHolder = new GeneratedKeyHolder();
                MapSqlParameterSource paramSource = new MapSqlParameterSource(preparedData);
                
                jdbc.update(sql, paramSource, keyHolder);
                
                return keyHolder.getKeys();
                // Return generated ID if available
                // Handle case where multiple keys are returned
                // if (keyHolder.getKeys() != null && !keyHolder.getKeys().isEmpty()) {
                //     Map<String, Object> keys = keyHolder.getKeys();
                //     // Try common ID column names
                //     for (String idColumn : List.of("id", "ID", "Id")) {
                //         if (keys.containsKey(idColumn)) {
                //             Object idValue = keys.get(idColumn);
                //             return idValue instanceof Number ? ((Number) idValue).longValue() : null;
                //         }
                //     }
                //     // If no standard ID column, return first numeric value
            //     for (Object value : keys.values()) {
            //         if (value instanceof Number) {
            //             return ((Number) value).longValue();
            //         }
            //     }
            // }
            // return null;
            });
        } catch (DataAccessException e) {
            handleDatabaseException(e, tableName, "INSERT");
            throw e; // Never reached, handleDatabaseException always throws
        }
    }

    /**
     * Batch insert multiple rows into a table
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name
     * @param dataList List of column name -> value maps
     * @return Number of rows inserted
     */
    public int batchInsert(String dbConnCode, String tableName, List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }

        //TODO
        Map<String, String> fieldCodeToColumnMap = new HashMap<>();
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        return txTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
            
            // Prepare all data rows
            List<Map<String, Object>> preparedDataList = dataList.stream()
                .map(data -> prepareData(data, fieldCodeToColumnMap))
                .toList();
            
            // Use first row to determine columns
            Map<String, Object> firstRow = preparedDataList.get(0);
            String columns = String.join(", ", firstRow.keySet());
            String params = firstRow.keySet().stream()
                .map(col -> ":" + col)
                .collect(Collectors.joining(", "));
            
            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", 
                tableName, columns, params);
            
            log.debug("Executing batch insert: {} with {} rows", sql, dataList.size());
            
            // Convert to array of SqlParameterSource
            MapSqlParameterSource[] batchParams = preparedDataList.stream()
                .map(MapSqlParameterSource::new)
                .toArray(MapSqlParameterSource[]::new);
            
            int[] results = jdbc.batchUpdate(sql, batchParams);
            return results.length;
        });
    }

    /**
     * Insert with UPSERT (INSERT ... ON CONFLICT DO UPDATE)
     * Works for PostgreSQL
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name
     * @param data Column name -> value map
     * @param conflictColumns Columns that define uniqueness
     * @return Number of affected rows
     */
    public int upsert(String dbConnCode, String tableName, Map<String, Object> data, 
                      List<String> conflictColumns) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be empty");
        }
        if (conflictColumns == null || conflictColumns.isEmpty()) {
            throw new IllegalArgumentException("Conflict columns cannot be empty");
        }
        //TODO
        Map<String, String> fieldCodeToColumnMap = new HashMap<>();
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        return txTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
            
            // Prepare data (convert Map/List to JSONB)
            Map<String, Object> preparedData = prepareData(data, fieldCodeToColumnMap);
            
            // Build INSERT statement
            String columns = String.join(", ", preparedData.keySet());
            String params = preparedData.keySet().stream()
                .map(col -> ":" + col)
                .collect(Collectors.joining(", "));
            
            // Build UPDATE SET clause (exclude conflict columns)
            String updateSet = preparedData.keySet().stream()
                .filter(col -> !conflictColumns.contains(col))
                .map(col -> col + " = EXCLUDED." + col)
                .collect(Collectors.joining(", "));
            
            String conflictCols = String.join(", ", conflictColumns);
            
            String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
                tableName, columns, params, conflictCols, updateSet
            );
            
            log.debug("Executing upsert: {} with data: {}", sql, data);
            
            MapSqlParameterSource paramSource = new MapSqlParameterSource(preparedData);
            return jdbc.update(sql, paramSource);
        });
    }

    /**
     * Update rows in a table
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name
     * @param data Column name -> value map to update
     * @param whereClause WHERE clause (e.g., "id = :id")
     * @param whereParams Parameters for WHERE clause
     * @return Number of affected rows
     */
    public int update(String dbConnCode, String tableName, Map<String, Object> data,
                     String whereClause, Map<String, Object> whereParams) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be empty");
        }
        Map<String, String> fieldCodeToColumnMap = new HashMap<>();
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        return txTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
            
            // Prepare data (convert Map/List to JSONB)
            Map<String, Object> preparedData = prepareData(data, fieldCodeToColumnMap);
            
            // Build UPDATE SET clause
            String setClause = preparedData.keySet().stream()
                .map(col -> col + " = :" + col)
                .collect(Collectors.joining(", "));
            
            String sql = String.format("UPDATE %s SET %s", tableName, setClause);
            
            if (whereClause != null && !whereClause.isBlank()) {
                sql += " WHERE " + whereClause;
            }
            
            log.debug("Executing update: {} with data: {}", sql, preparedData);
            
            // Merge prepared data and where params
            MapSqlParameterSource paramSource = new MapSqlParameterSource(preparedData);
            if (whereParams != null) {
                paramSource.addValues(whereParams);
            }
            
            return jdbc.update(sql, paramSource);
        });
    }

    /**
     * Delete rows from a table
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name
     * @param whereClause WHERE clause (e.g., "id = :id")
     * @param whereParams Parameters for WHERE clause
     * @return Number of affected rows
     */
    public int delete(String dbConnCode, String tableName, 
                     String whereClause, Map<String, Object> whereParams) {
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        return txTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
            
            String sql = "DELETE FROM " + tableName;
            
            if (whereClause != null && !whereClause.isBlank()) {
                sql += " WHERE " + whereClause;
            }
            
            log.debug("Executing delete: {} with params: {}", sql, whereParams);
            
            MapSqlParameterSource paramSource = whereParams != null 
                ? new MapSqlParameterSource(whereParams) 
                : new MapSqlParameterSource();
            
            return jdbc.update(sql, paramSource);
        });
    }

    /**
     * Execute a custom SQL statement
     * 
     * @param dbConnCode Database connection code
     * @param sql SQL statement with named parameters
     * @param params Parameters map
     * @return Number of affected rows
     */
    public int execute(String dbConnCode, String sql, Map<String, Object> params) {
        TransactionTemplate txTemplate = createTransactionTemplate(dbConnCode);
        
        return txTemplate.execute(status -> {
            NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
            
            log.debug("Executing custom SQL: {} with params: {}", sql, params);
            
            MapSqlParameterSource paramSource = params != null 
                ? new MapSqlParameterSource(params) 
                : new MapSqlParameterSource();
            
            return jdbc.update(sql, paramSource);
        });
    }

    /**
     * Query data from a table
     * 
     * @param dbConnCode Database connection code
     * @param sql SELECT SQL statement
     * @param params Parameters map
     * @return List of rows as maps
     */
    public List<Map<String, Object>> query(String dbConnCode, String sql, Map<String, Object> params) {
        NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
        
        log.debug("Executing query: {} with params: {}", sql, params);
        
        MapSqlParameterSource paramSource = params != null 
            ? new MapSqlParameterSource(params) 
            : new MapSqlParameterSource();
        
        return jdbc.queryForList(sql, paramSource);
    }

    /**
     * Count rows in a table
     * 
     * @param dbConnCode Database connection code
     * @param tableName Target table name
     * @param whereClause Optional WHERE clause
     * @param whereParams Parameters for WHERE clause
     * @return Row count
     */
    public long count(String dbConnCode, String tableName, 
                     String whereClause, Map<String, Object> whereParams) {
        NamedParameterJdbcTemplate jdbc = dataSourceService.getJdbcTemplate(dbConnCode);
        
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        if (whereClause != null && !whereClause.isBlank()) {
            sql += " WHERE " + whereClause;
        }
        
        log.debug("Executing count: {} with params: {}", sql, whereParams);
        
        MapSqlParameterSource paramSource = whereParams != null 
            ? new MapSqlParameterSource(whereParams) 
            : new MapSqlParameterSource();
        
        Long count = jdbc.queryForObject(sql, paramSource, Long.class);
        return count != null ? count : 0L;
    }
}
