package com.rrhh.contratos.dto.response;

import java.math.BigDecimal;

public record LiquidacionResponse(
        String id,
        String contratoId,
        String periodo,
        BigDecimal sueldoBase,
        BigDecimal afpDescuento,
        BigDecimal saludDescuento,
        BigDecimal seguroCesantia,
        BigDecimal impuestoSegundaCategoria,
        BigDecimal sueldoLiquido,
        String estado
) {}
