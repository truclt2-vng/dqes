package com.a4b.dqes.datasource.exception;

import lombok.Getter;

/**
 * Exception thrown when a NOT NULL constraint violation occurs
 */
@Getter
public class NotNullViolationException extends DataOperationException {
    
    private final String tableName;
    private final String columnName;
    
    public NotNullViolationException(String message, String tableName, String columnName) {
        super(message);
        this.tableName = tableName;
        this.columnName = columnName;
    }
    
    public NotNullViolationException(String message, String tableName, String columnName, Throwable cause) {
        super(message, cause);
        this.tableName = tableName;
        this.columnName = columnName;
    }
}
