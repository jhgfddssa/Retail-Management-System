package com.retail.common.api;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()); p.setTitle("Invalid request"); return p; }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed"); p.setTitle("Invalid request"); return p; }
  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail authentication(AuthenticationException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password"); p.setTitle("Authentication failed"); return p; }
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden(AccessDeniedException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not authorized to perform this action"); p.setTitle("Forbidden"); return p; }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail dataConflict(DataIntegrityViolationException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A record conflicts with an existing value"); p.setTitle("Data conflict"); return p; }
  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail conflict(IllegalStateException ex) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()); p.setTitle("Business rule conflict"); return p; }
}