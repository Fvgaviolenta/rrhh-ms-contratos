package com.rrhh.contratos.exception;

import com.rrhh.contratos.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        List<ApiResponse.ErrorItem> errores = ex.getCampo() == null ? List.of() : List.of(new ApiResponse.ErrorItem(ex.getCampo(), ex.getMessage()));
        return ResponseEntity.status(ex.getCodigo()).body(ApiResponse.error(ex.getCodigo(), ex.getMessage(), errores));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.ErrorItem> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiResponse.ErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Validación fallida", errores));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, "No autorizado para este recurso", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, "Error interno", List.of()));
    }
}
