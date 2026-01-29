package com.a4b.dqes.datasource.exception;

import lombok.Getter;

/**
 * Exception thrown when a duplicate key violation occurs
 */
@Getter
public class DuplicateKeyException extends DataOperationException {
    
    private final String tableName;
    private final String constraintName;
    private final String duplicateKey;
    
    public DuplicateKeyException(String message, String tableName, String constraintName, String duplicateKey) {
        super(message);
        this.tableName = tableName;
        this.constraintName = constraintName;
        this.duplicateKey = duplicateKey;
    }
    
    public DuplicateKeyException(String message, String tableName, String constraintName, String duplicateKey, Throwable cause) {
        super(message, cause);
        this.tableName = tableName;
        this.constraintName = constraintName;
        this.duplicateKey = duplicateKey;
    }
}
