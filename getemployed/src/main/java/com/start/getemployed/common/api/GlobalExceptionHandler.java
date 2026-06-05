package com.start.getemployed.common.api;

import com.start.getemployed.exception.ResourceAlreadyExistsException;
import com.start.getemployed.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorEnvelope> notFound(RuntimeException ex) {
    return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(ResourceAlreadyExistsException.class)
  public ResponseEntity<ErrorEnvelope> conflict(RuntimeException ex) {
    return error(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS", ex.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorEnvelope> badRequest(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorEnvelope> unauthorized(Exception ex) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> internal(Exception ex) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage());
  }

  private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ErrorEnvelope.of(code, message, status.value()));
  }
}
