package com.example.bankcards.exception;

import com.example.bankcards.util.ErrorResponseUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ErrorResponseUtil errorResponseUtil;
    public GlobalExceptionHandler(ErrorResponseUtil errorResponseUtil) {
        this.errorResponseUtil = errorResponseUtil;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> details = new HashMap<>();
        details.put("validationErrors", errors);

        return errorResponseUtil.buildErrorResponseEntity(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            details
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFoundException(UserNotFoundException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<Object> handleRoleNotFoundException(RoleNotFoundException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Object> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RoleAlreadyAssignedException.class)
    public ResponseEntity<Object> handleRoleAlreadyAssignedException(RoleAlreadyAssignedException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RoleNotAssignedException.class)
    public ResponseEntity<Object> handleRoleNotAssignedException(RoleNotAssignedException ex) {
        return errorResponseUtil.buildErrorResponseEntity(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

}

