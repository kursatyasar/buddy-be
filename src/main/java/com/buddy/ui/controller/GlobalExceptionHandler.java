package com.buddy.ui.controller;

import com.buddy.ui.model.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("Validation error: {}", errorMessage);
        
        ErrorResponse errorResponse = new ErrorResponse(
                "Validation failed: " + errorMessage,
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(FORMATTER)
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().format(FORMATTER)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
//    @ExceptionHandler(org.springframework.ai.chat.client.ChatClientException.class)
//    public ResponseEntity<ErrorResponse> handleAiServiceException(
//            org.springframework.ai.chat.client.ChatClientException ex, WebRequest request) {
//
//        log.error("AI service error occurred", ex);
//
//        ErrorResponse errorResponse = new ErrorResponse(
//                "AI service error: " + ex.getMessage(),
//                HttpStatus.SERVICE_UNAVAILABLE.value(),
//                LocalDateTime.now().format(FORMATTER)
//        );
//
//        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
//    }
    
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseException(
            org.springframework.dao.DataAccessException ex, WebRequest request) {
        
        log.error("Database error occurred", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                "Database error: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().format(FORMATTER)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

