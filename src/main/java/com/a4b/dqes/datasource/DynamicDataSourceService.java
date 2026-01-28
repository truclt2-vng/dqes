package com.a4b.dqes.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.a4b.dqes.crypto.CryptoService;
import com.a4b.dqes.dto.schemacache.DbConnInfoDto;
import com.a4b.dqes.service.CfgtbDbconnInfoService;
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
    
    private final CfgtbDbconnInfoService dbconnInfoService; 
    private final CryptoService cryptoService;
    
    // Cache: dbconnCode -> NamedParameterJdbcTemplate
    private final Map<String, NamedParameterJdbcTemplate> templateCache = new ConcurrentHashMap<>();
    
    // Cache: dbconnCode -> DataSource (for lifecycle management)
    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();
    

    public HikariDataSource getDataSource(String dbconnCode) {
        getJdbcTemplate(dbconnCode);
        return dataSourceCache.get(dbconnCode);
    }
    /**
     * Get NamedParameterJdbcTemplate for the specified dbconn_id
     * Creates and caches if not exists
     */
    public NamedParameterJdbcTemplate getJdbcTemplate(String dbconnCode) {
        if (dbconnCode == null) {
            throw new IllegalArgumentException("dbconnCode cannot be null");
        }
        
        return templateCache.computeIfAbsent(dbconnCode, code -> {
            log.info("Creating new JDBC template for dbconnCode={}", code);
            
            try {
                // Load connection info from cfgtb_dbconnInfo
                DbConnInfoDto connInfo = dbconnInfoService.getDbConnInfoByCode(code);
                if (connInfo == null) {
                    throw new IllegalArgumentException("No connection info found for dbconnCode=" + code);
                }
                // Build DataSource
                HikariDataSource dataSource = buildDataSource(connInfo);
                dataSourceCache.put(code, dataSource);
                
                // Create NamedParameterJdbcTemplate
                return new NamedParameterJdbcTemplate(dataSource);
                
            } catch (Exception e) {
                log.error("Failed to create JDBC template for dbconnCode={}", code, e);
                throw new RuntimeException("Failed to initialize DataSource for dbconnCode=" + code, e);
            }
        });
    }
    
    /**
     * Build HikariDataSource from connection info
     */
    public HikariDataSource buildDataSource(DbConnInfoDto connInfo) {
        String schema = (connInfo.getDbSchema() == null || connInfo.getDbSchema().isBlank()) 
            ? "public" 
            : connInfo.getDbSchema();
        
        // Decrypt password
        String passwordPlain = cryptoService.decrypt(
            connInfo.getPasswordEnc(), 
            connInfo.getPasswordAlg()
        );
        
        // Build JDBC URL based on vendor
        String url = buildJdbcUrl(connInfo, schema);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(connInfo.getUsername());
        config.setPassword(passwordPlain);
        config.setMaximumPoolSize(10);  // Adjust based on load
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("dqes-query-" + connInfo.getConnCode());
        
        // Additional properties for better performance
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
    
    /**
     * Build JDBC URL based on database vendor
     */
    private String buildJdbcUrl(DbConnInfoDto connInfo, String schema) {
        StringBuilder url = new StringBuilder();
        
        switch (connInfo.getDbVendor().toUpperCase()) {
            case "POSTGRES", "POSTGRESQL" -> {
                url.append("jdbc:postgresql://")
                   .append(connInfo.getHost())
                   .append(":")
                   .append(connInfo.getPort())
                   .append("/")
                   .append(connInfo.getDbName())
                   .append("?currentSchema=").append(schema);
                
                if (Boolean.TRUE.equals(connInfo.getSslEnabled())) {
                    url.append("&ssl=true");
                    if (connInfo.getSslMode() != null && !connInfo.getSslMode().isBlank()) {
                        url.append("&sslmode=").append(connInfo.getSslMode());
                    }
                }
            }
            case "MYSQL" -> {
                url.append("jdbc:mysql://")
                   .append(connInfo.getHost())
                   .append(":")
                   .append(connInfo.getPort())
                   .append("/")
                   .append(connInfo.getDbName())
                   .append("?useSSL=").append(connInfo.getSslEnabled());
            }
            case "ORACLE" -> {
                url.append("jdbc:oracle:thin:@")
                   .append(connInfo.getHost())
                   .append(":")
                   .append(connInfo.getPort())
                   .append(":")
                   .append(connInfo.getDbName());
            }
            case "SQLSERVER", "MSSQL" -> {
                url.append("jdbc:sqlserver://")
                   .append(connInfo.getHost())
                   .append(":")
                   .append(connInfo.getPort())
                   .append(";databaseName=")
                   .append(connInfo.getDbName());
                
                if (Boolean.TRUE.equals(connInfo.getSslEnabled())) {
                    url.append(";encrypt=true");
                }
            }
            default -> throw new IllegalArgumentException(
                "Unsupported database vendor: " + connInfo.getDbVendor()
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
