package com.example.bankcards.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


@Component
public class ErrorResponseUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> createErrorResponseMap(HttpStatus status, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("timestamp", new Date());
        return errorResponse;
    }

    public Map<String, Object> createErrorResponseMap(HttpStatus status, String message, Map<String, Object> details) {
        Map<String, Object> errorResponse = createErrorResponseMap(status, message);
        errorResponse.put("details", details);
        return errorResponse;
    }

    public ResponseEntity<Object> buildErrorResponseEntity(HttpStatus status, String message) {
        return new ResponseEntity<>(createErrorResponseMap(status, message), status);
    }

    public ResponseEntity<Object> buildErrorResponseEntity(HttpStatus status, String message, Map<String, Object> details) {
        return new ResponseEntity<>(createErrorResponseMap(status, message, details), status);
    }

    public void setErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        Map<String, Object> errorResponse = createErrorResponseMap(status, message);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    public void setErrorResponse(HttpServletResponse response, HttpStatus status, String message, Map<String, Object> details) throws IOException {
        Map<String, Object> errorResponse = createErrorResponseMap(status, message, details);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}