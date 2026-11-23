package com.rrhh.contratos.exception;

import com.rrhh.contratos.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Errores de la API con el formato {@link ApiResponse}.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para que las excepciones propias de Spring MVC
 * (ruta inexistente, método no permitido, JSON mal formado, parámetro faltante o con tipo inválido,
 * content type no soportado...) respondan con su status 4xx y no caigan en el handler genérico como 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        List<ApiResponse.ErrorItem> errores = ex.getCampo() == null ? List.of() : List.of(new ApiResponse.ErrorItem(ex.getCampo(), ex.getMessage()));
        return ResponseEntity.status(ex.getCodigo()).body(ApiResponse.error(ex.getCodigo(), ex.getMessage(), errores));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ApiResponse.ErrorItem> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiResponse.ErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, ApiResponse.error(400, "Validación fallida", errores), headers, status, request);
    }

    // Todas las excepciones de Spring MVC pasan por aquí: se conserva el status y los headers (Allow en un 405)
    // y el cuerpo pasa a ApiResponse, sin el detalle interno de la excepción.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        Object cuerpo = body instanceof ApiResponse<?> ? body
                : ApiResponse.error(statusCode.value(), mensaje(statusCode), List.of());
        return super.handleExceptionInternal(ex, cuerpo, headers, statusCode, request);
    }

    private static String mensaje(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato de respuesta no aceptable";
            case 413 -> "Contenido demasiado grande";
            case 415 -> "Tipo de contenido no soportado";
            case 503 -> "Servicio no disponible";
            default -> status.is5xxServerError() ? "Error interno" : "Solicitud inválida";
        };
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
