package com.rrhh.contratos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Emision de un contrato desde la plantilla: solo campos {@code rrhh_*}. */
public record EmitirContratoRequest(
        @NotBlank String trabajadorId,
        @NotNull Map<String, String> campos
) {}
