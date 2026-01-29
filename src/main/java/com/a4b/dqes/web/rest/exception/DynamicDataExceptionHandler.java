package com.a4b.dqes.web.rest.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.a4b.dqes.datasource.exception.DataOperationException;
import com.a4b.dqes.datasource.exception.DuplicateKeyException;
import com.a4b.dqes.datasource.exception.ForeignKeyViolationException;
import com.a4b.dqes.datasource.exception.NotNullViolationException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for Dynamic Data API
 */
@Slf4j
@RestControllerAdvice
public class DynamicDataExceptionHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(
        DuplicateKeyException ex, WebRequest request) {
        
        log.warn("Duplicate key violation: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "DUPLICATE_KEY",
            "Duplicate entry: " + ex.getDuplicateKey(),
            new ErrorDetails(
                ex.getTableName(),
                ex.getConstraintName(),
                ex.getDuplicateKey()
            ),
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ForeignKeyViolationException.class)
    public ResponseEntity<ErrorResponse> handleForeignKeyViolation(
        ForeignKeyViolationException ex, WebRequest request) {
        
        log.warn("Foreign key violation: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "FOREIGN_KEY_VIOLATION",
            "Cannot delete: Record is still referenced by " + ex.getReferencedTable(),
            new ErrorDetails(
                ex.getTableName(),
                ex.getConstraintName(),
                "Referenced by: " + ex.getReferencedTable()
            ),
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(NotNullViolationException.class)
    public ResponseEntity<ErrorResponse> handleNotNullViolation(
        NotNullViolationException ex, WebRequest request) {
        
        log.warn("Not null violation: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "NOT_NULL_VIOLATION",
            "Column '" + ex.getColumnName() + "' cannot be null",
            new ErrorDetails(
                ex.getTableName(),
                ex.getColumnName(),
                "Required field"
            ),
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataOperationException.class)
    public ResponseEntity<ErrorResponse> handleDataOperationException(
        DataOperationException ex, WebRequest request) {
        
        log.error("Data operation error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "DATA_OPERATION_ERROR",
            ex.getMessage(),
            null,
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
        IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Invalid argument: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_ARGUMENT",
            ex.getMessage(),
            null,
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex, WebRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            null,
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private ErrorDetails details;
        private LocalDateTime timestamp;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorDetails {
        private String tableName;
        private String constraintOrColumn;
        private String additionalInfo;
    }
}
