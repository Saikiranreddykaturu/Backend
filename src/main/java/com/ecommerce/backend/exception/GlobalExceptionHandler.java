package com.ecommerce.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return new ResponseEntity<>(
                Map.of("error", ex.getMessage()),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<?> handleAlreadyExists(ResourceAlreadyExistsException ex) {
        return new ResponseEntity<>(
                Map.of("error", ex.getMessage()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ResponseEntity<>(
                Map.of("error", ex.getMessage()),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequest(BadRequestException ex) {
        return new ResponseEntity<>(
                Map.of("error", ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        return new ResponseEntity<>(
                Map.of("error", "Something went wrong"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
