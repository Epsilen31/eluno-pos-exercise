package com.eluno.pos.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BillingValidationException.class)
    public ResponseEntity<?> handleBillingValidation(BillingValidationException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(UpstreamApiException.class)
    public ResponseEntity<?> handleUpstreamFailure(UpstreamApiException exception) {
        return ResponseEntity.status(503)
                .body(
                        Map.of(
                                "error",
                                exception.getMessage(),
                                "uncertain",
                                exception.isOutcomeUncertain()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleRequestValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "Invalid request",
                                "details",
                                exception.getBindingResult().getFieldErrors().stream()
                                        .map(
                                                fieldError ->
                                                        fieldError.getField()
                                                                + ": "
                                                                + fieldError.getDefaultMessage())
                                        .toList()));
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<?> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                "Invalid request. Check required fields, types and allowed values."));
    }
}
