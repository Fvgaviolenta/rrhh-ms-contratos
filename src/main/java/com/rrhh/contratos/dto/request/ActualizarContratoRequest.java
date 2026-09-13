package com.rrhh.contratos.dto.request;

import java.math.BigDecimal;

public record ActualizarContratoRequest(
        BigDecimal salarioBase,
        String tipoContrato
) {}
