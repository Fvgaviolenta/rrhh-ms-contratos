package com.rrhh.contratos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoResponse(
        String id,
        String tenantId,
        String trabajadorId,
        BigDecimal salarioBase,
        String tipoContrato,
        LocalDate fechaInicio,
        LocalDate fechaTermino,
        boolean activo
) {}
