package com.a4b.dqes.datasource.exception;

import lombok.Getter;

/**
 * Exception thrown when a foreign key constraint violation occurs
 */
@Getter
public class ForeignKeyViolationException extends DataOperationException {
    
    private final String tableName;
    private final String constraintName;
    private final String referencedTable;
    
    public ForeignKeyViolationException(String message, String tableName, String constraintName, String referencedTable) {
        super(message);
        this.tableName = tableName;
        this.constraintName = constraintName;
        this.referencedTable = referencedTable;
    }
    
    public ForeignKeyViolationException(String message, String tableName, String constraintName, String referencedTable, Throwable cause) {
        super(message, cause);
        this.tableName = tableName;
        this.constraintName = constraintName;
        this.referencedTable = referencedTable;
    }
}
