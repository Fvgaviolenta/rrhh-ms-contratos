package com.rrhh.contratos.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidacionCalculatorTest {

    @Test
    void calculaDescuentosSimplificados() {
        var r = LiquidacionCalculator.calcular(new BigDecimal("1000000"), "INDEFINIDO");
        assertTrue(r.afpDescuento().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(r.saludDescuento().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(r.seguroCesantia().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(r.sueldoLiquido().compareTo(new BigDecimal("1000000")) < 0);
    }
}
