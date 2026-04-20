package com.proconsi.electrobazar.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all controllers in the application.
 * Intercepts exceptions and transforms them into consistent JSON error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles 404 errors for non-existent static resources or endpoints.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        String path = ex.getResourcePath();
        if (path != null && !path.contains(".well-known") && !path.endsWith(".map") && !path.endsWith(".json")) {
            log.error("Resource not found: {}", ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles custom ResourceNotFoundException.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles custom DuplicateResourceException.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
        log.error("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles custom InsufficientCashException.
     */
    @ExceptionHandler(InsufficientCashException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientCash(InsufficientCashException ex) {
        log.error("Insufficient cash: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles standard illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles Bean Validation errors (e.g., @NotNull, @Size).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("Validation error: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("Validation error: " + errors));
    }

    /**
     * Handles exceptions related to invalid business states.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles database constraint violations (FK, Unique, etc.).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = "The operation could not be completed due to a data integrity constraint.";
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();

        log.error("Data integrity violation: {}", rootMsg);

        if (rootMsg != null) {
            String lowerMsg = rootMsg.toLowerCase();
            if (lowerMsg.contains("foreign key") || lowerMsg.contains("referential integrity")) {
                message = "Cannot delete item because it is referenced by other records (sales, invoices, etc.).";
            } else if (lowerMsg.contains("duplicate") || lowerMsg.contains("unique constraint")) {
                message = "A record with these unique values already exists.";
            }
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(message));
    }

    /**
     * Handles cases where the client closes the connection before the server finishes responding.
     * Prevents log pollution with stack traces for normal user behavior (canceling search, refreshing page).
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    public void handleClientAbort(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (ex instanceof AsyncRequestNotUsableException || msg.contains("broken pipe") || msg.contains("connection reset by peer")) {
            log.warn("Client aborted request: {} (Normal behavior during search/navigation)", ex.getClass().getSimpleName());
        } else if (ex instanceof IOException) {
            // Re-throw if it's a real IOException not related to client abortion
            log.error("Unhandled I/O error: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Fallback for any unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Internal server error: " + ex.getMessage()));
    }

    /**
     * Builds the standard error response body.
     */
    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "error", message);
    }
}