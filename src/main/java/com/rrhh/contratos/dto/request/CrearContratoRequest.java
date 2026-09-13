package com.rrhh.contratos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearContratoRequest(
        @NotBlank String trabajadorId,
        @NotNull @DecimalMin("0.01") BigDecimal salarioBase,
        @NotBlank String tipoContrato,
        @NotNull LocalDate fechaInicio
) {}
