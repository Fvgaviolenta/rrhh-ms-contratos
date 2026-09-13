package com.rrhh.contratos.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CalcularLiquidacionRequest(
        @NotBlank String contratoId,
        @NotBlank String periodo
) {}
