package com.rrhh.contratos.exception;

import com.rrhh.contratos.dto.ApiResponse;

import java.util.List;

/** Uno o mas campos de la plantilla son invalidos; responde 400 "Validación fallida" con un error por campo. */
public class ValidacionCamposException extends RuntimeException {
    private final List<ApiResponse.ErrorItem> errores;

    public ValidacionCamposException(List<ApiResponse.ErrorItem> errores) {
        super("Validación fallida");
        this.errores = List.copyOf(errores);
    }

    public List<ApiResponse.ErrorItem> getErrores() { return errores; }
}
