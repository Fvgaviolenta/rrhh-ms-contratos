package com.rrhh.contratos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int codigo, String mensaje, T datos, List<ErrorItem> errores) {
    public static <T> ApiResponse<T> ok(T datos, String mensaje) { return new ApiResponse<>(200, mensaje, datos, List.of()); }
    public static <T> ApiResponse<T> created(T datos, String mensaje) { return new ApiResponse<>(201, mensaje, datos, List.of()); }
    public static ApiResponse<Void> error(int codigo, String mensaje, List<ErrorItem> errores) {
        return new ApiResponse<>(codigo, mensaje, null, errores);
    }
    public record ErrorItem(String campo, String detalle) {}
}
