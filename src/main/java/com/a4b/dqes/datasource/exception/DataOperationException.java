package com.a4b.dqes.datasource.exception;

/**
 * Base exception for data operation errors
 */
public class DataOperationException extends RuntimeException {
    
    public DataOperationException(String message) {
        super(message);
    }
    
    public DataOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
