package com.ecoloop.common.web;

import com.ecoloop.common.RateLimitException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        log.warn("Rate limit triggered: {}", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(errorBody(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        String firstMessage = fieldErrors.values().stream().findFirst().orElse("Validation failed");
        log.warn("Validation error: {}", fieldErrors);
        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, firstMessage);
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed or missing request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, "Required request body is missing or malformed"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(com.ecoloop.common.DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(com.ecoloop.common.DomainException ex) {
        log.warn("Domain exception: internalDetail='{}', clientSafeMessage='{}'", ex.getMessage(), ex.getClientSafeMessage());
        return ResponseEntity.status(ex.getStatus())
            .body(errorBody(ex.getStatus(), ex.getClientSafeMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, "Invalid request argument"));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleAuthentication(Exception ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(errorBody(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(errorBody(HttpStatus.FORBIDDEN, "Insufficient permissions"));
    }

    @ExceptionHandler(CsrfException.class)
    public ResponseEntity<Map<String, Object>> handleCsrf(CsrfException ex) {
        log.warn("CSRF verification failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(errorBody(HttpStatus.FORBIDDEN, "Invalid or missing CSRF token"));
    }

    @ExceptionHandler({NoSuchElementException.class, NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        log.info("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage() != null ? ex.getMessage() : "Resource not found"));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(Exception ex) {
        log.warn("Conflict error: {}", ex.getMessage());
        String msg = ex instanceof DataIntegrityViolationException ? "Data integrity constraint violated" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody(HttpStatus.CONFLICT, msg));
    }

    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class,
        QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<Map<String, Object>> handleLockFailure(Exception ex) {
        log.warn("Database lock or transaction timeout: {}", ex.getMessage());
        String message;
        if (ex instanceof CannotAcquireLockException) {
            message = "Unable to acquire exclusive record lock; please retry your request";
        } else if (ex instanceof QueryTimeoutException) {
            message = "Database query timed out; please retry your request";
        } else if (ex instanceof TransactionTimedOutException) {
            message = "Transaction timed out due to concurrent contention; please retry your request";
        } else {
            message = "Concurrent modification conflict detected; please retry your request";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody(HttpStatus.CONFLICT, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(errorBody(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the configured maximum limit"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
        log.warn("Multipart upload error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, "Invalid multipart upload request"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("ResponseStatusException 5xx: {}", ex.getMessage(), ex);
        } else {
            log.warn("ResponseStatusException {}: {}", ex.getStatusCode().value(), ex.getReason());
        }
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(errorBody(status, ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled server exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
    }

    private static Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? message : status.getReasonPhrase());
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
