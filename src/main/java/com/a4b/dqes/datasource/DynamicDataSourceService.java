package com.a4b.dqes.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.a4b.dqes.crypto.CryptoService;
import com.a4b.dqes.dto.record.DbConnInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic DataSource Service
 * 
 * Manages target DataSources and NamedParameterJdbcTemplates based on dbconn_id.
 * Each connection is cached and reused for efficiency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDataSourceService {
    
    private final NamedParameterJdbcTemplate dqesJdbc;  // For metadata queries
    private final CryptoService cryptoService;
    
    // Cache: dbconnId -> NamedParameterJdbcTemplate
    private final Map<Integer, NamedParameterJdbcTemplate> templateCache = new ConcurrentHashMap<>();
    
    // Cache: dbconnId -> DataSource (for lifecycle management)
    private final Map<Integer, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();
    
    /**
     * Get NamedParameterJdbcTemplate for the specified dbconn_id
     * Creates and caches if not exists
     */
    public NamedParameterJdbcTemplate getJdbcTemplate(String tenantCode, String appCode, Integer dbconnId) {
        if (dbconnId == null) {
            throw new IllegalArgumentException("dbconnId cannot be null");
        }
        
        return templateCache.computeIfAbsent(dbconnId, id -> {
            log.info("Creating new JDBC template for dbconnId={}, tenant={}, app={}", 
                id, tenantCode, appCode);
            
            try {
                // Load connection info from cfgtb_dbconn_info
                DbConnInfo connInfo = loadConnectionInfo(tenantCode, appCode, id);
                
                // Decrypt password
                String passwordPlain = cryptoService.decrypt(
                    connInfo.passwordEnc(), 
                    connInfo.passwordAlg()
                );
                
                // Build DataSource
                HikariDataSource dataSource = buildDataSource(connInfo, passwordPlain);
                dataSourceCache.put(id, dataSource);
                
                // Create NamedParameterJdbcTemplate
                return new NamedParameterJdbcTemplate(dataSource);
                
            } catch (Exception e) {
                log.error("Failed to create JDBC template for dbconnId={}", id, e);
                throw new RuntimeException("Failed to initialize DataSource for dbconnId=" + id, e);
            }
        });
    }
    
    /**
     * Load connection info from cfgtb_dbconn_info
     */
    public DbConnInfo loadConnectionInfo(String tenantCode, String appCode, Integer dbconnId) {
        String sql = """
            SELECT id, tenant_code, app_code, conn_code, db_vendor, host, port, 
                   db_name, db_schema, username, password_enc, password_alg, 
                   ssl_enabled, ssl_mode, jdbc_params::text
            FROM dqes.cfgtb_dbconn_info
            WHERE id = :dbconnId
              AND tenant_code = :tenantCode
              AND app_code = :appCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("dbconnId", dbconnId)
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode);
        
        return dqesJdbc.queryForObject(sql, params, (rs, i) -> new DbConnInfo(
            rs.getInt("id"),
            rs.getString("tenant_code"),
            rs.getString("app_code"),
            rs.getString("conn_code"),
            rs.getString("db_vendor"),
            rs.getString("host"),
            rs.getInt("port"),
            rs.getString("db_name"),
            rs.getString("db_schema"),
            rs.getString("username"),
            rs.getString("password_enc"),
            rs.getString("password_alg"),
            (Boolean) rs.getObject("ssl_enabled"),
            rs.getString("ssl_mode"),
            rs.getString("jdbc_params")
        ));
    }

    /**
     * Load connection info from cfgtb_dbconn_info
     */
    public DbConnInfo loadConnectionInfo(String tenantCode, String appCode, String dbconnCode) {
        String sql = """
            SELECT id, tenant_code, app_code, conn_code, db_vendor, host, port, 
                   db_name, db_schema, username, password_enc, password_alg, 
                   ssl_enabled, ssl_mode, jdbc_params::text
            FROM dqes.cfgtb_dbconn_info
            WHERE conn_code = :dbconnCode
              AND tenant_code = :tenantCode
              AND app_code = :appCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("dbconnCode", dbconnCode)
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode);
        
        return dqesJdbc.queryForObject(sql, params, (rs, i) -> new DbConnInfo(
            rs.getInt("id"),
            rs.getString("tenant_code"),
            rs.getString("app_code"),
            rs.getString("conn_code"),
            rs.getString("db_vendor"),
            rs.getString("host"),
            rs.getInt("port"),
            rs.getString("db_name"),
            rs.getString("db_schema"),
            rs.getString("username"),
            rs.getString("password_enc"),
            rs.getString("password_alg"),
            (Boolean) rs.getObject("ssl_enabled"),
            rs.getString("ssl_mode"),
            rs.getString("jdbc_params")
        ));
    }
    
    /**
     * Build HikariDataSource from connection info
     */
    public HikariDataSource buildDataSource(DbConnInfo conn, String passwordPlain) {
        String schema = (conn.dbSchema() == null || conn.dbSchema().isBlank()) 
            ? "public" 
            : conn.dbSchema();
        
        // Build JDBC URL based on vendor
        String url = buildJdbcUrl(conn, schema);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(conn.username());
        config.setPassword(passwordPlain);
        config.setMaximumPoolSize(10);  // Adjust based on load
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("dqes-query-" + conn.connCode());
        
        // Additional properties for better performance
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
    
    /**
     * Build JDBC URL based on database vendor
     */
    private String buildJdbcUrl(DbConnInfo conn, String schema) {
        StringBuilder url = new StringBuilder();
        
        switch (conn.dbVendor().toUpperCase()) {
            case "POSTGRES", "POSTGRESQL" -> {
                url.append("jdbc:postgresql://")
                   .append(conn.host())
                   .append(":")
                   .append(conn.port())
                   .append("/")
                   .append(conn.dbName())
                   .append("?currentSchema=").append(schema);
                
                if (Boolean.TRUE.equals(conn.sslEnabled())) {
                    url.append("&ssl=true");
                    if (conn.sslMode() != null && !conn.sslMode().isBlank()) {
                        url.append("&sslmode=").append(conn.sslMode());
                    }
                }
            }
            case "MYSQL" -> {
                url.append("jdbc:mysql://")
                   .append(conn.host())
                   .append(":")
                   .append(conn.port())
                   .append("/")
                   .append(conn.dbName())
                   .append("?useSSL=").append(conn.sslEnabled());
            }
            case "ORACLE" -> {
                url.append("jdbc:oracle:thin:@")
                   .append(conn.host())
                   .append(":")
                   .append(conn.port())
                   .append(":")
                   .append(conn.dbName());
            }
            case "SQLSERVER", "MSSQL" -> {
                url.append("jdbc:sqlserver://")
                   .append(conn.host())
                   .append(":")
                   .append(conn.port())
                   .append(";databaseName=")
                   .append(conn.dbName());
                
                if (Boolean.TRUE.equals(conn.sslEnabled())) {
                    url.append(";encrypt=true");
                }
            }
            default -> throw new IllegalArgumentException(
                "Unsupported database vendor: " + conn.dbVendor()
            );
        }
        
        return url.toString();
    }
    
    /**
     * Close all cached DataSources (for cleanup)
     */
    public void closeAll() {
        log.info("Closing {} cached DataSources", dataSourceCache.size());
        
        dataSourceCache.forEach((id, ds) -> {
            try {
                if (!ds.isClosed()) {
                    ds.close();
                    log.debug("Closed DataSource for dbconnId={}", id);
                }
            } catch (Exception e) {
                log.error("Error closing DataSource for dbconnId={}", id, e);
            }
        });
        
        dataSourceCache.clear();
        templateCache.clear();
    }
    
    /**
     * Remove specific connection from cache
     */
    public void evict(Integer dbconnId) {
        log.info("Evicting cached connection for dbconnId={}", dbconnId);
        
        HikariDataSource ds = dataSourceCache.remove(dbconnId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
        
        templateCache.remove(dbconnId);
    }
}
