package com.rrhh.contratos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LiquidacionCalculator {

    private LiquidacionCalculator() {}

    public record Resultado(
            BigDecimal afpDescuento,
            BigDecimal saludDescuento,
            BigDecimal seguroCesantia,
            BigDecimal impuestoSegundaCategoria,
            BigDecimal sueldoLiquido
    ) {}

    public static Resultado calcular(BigDecimal sueldoBase, String tipoContrato) {
        BigDecimal afp = pct(sueldoBase, "0.1115");
        BigDecimal salud = pct(sueldoBase, "0.07");
        BigDecimal cesantia = "INDEFINIDO".equalsIgnoreCase(tipoContrato) ? pct(sueldoBase, "0.006") : BigDecimal.ZERO;
        BigDecimal baseTributable = sueldoBase.subtract(afp).subtract(salud).subtract(cesantia).max(BigDecimal.ZERO);
        BigDecimal impuesto = impuestoSegundaCategoria(baseTributable);
        BigDecimal liquido = sueldoBase.subtract(afp).subtract(salud).subtract(cesantia).subtract(impuesto);
        return new Resultado(afp, salud, cesantia, impuesto, liquido.max(BigDecimal.ZERO));
    }

    private static BigDecimal pct(BigDecimal base, String rate) {
        return base.multiply(new BigDecimal(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    // Tramos simplificados sobre base tributable mensual en CLP
    private static BigDecimal impuestoSegundaCategoria(BigDecimal base) {
        BigDecimal impuesto = BigDecimal.ZERO;
        if (base.compareTo(new BigDecimal("1500000")) <= 0) {
            impuesto = pct(base, "0.00");
        } else if (base.compareTo(new BigDecimal("2500000")) <= 0) {
            impuesto = pct(base.subtract(new BigDecimal("1500000")), "0.04");
        } else if (base.compareTo(new BigDecimal("3500000")) <= 0) {
            impuesto = new BigDecimal("40000").add(pct(base.subtract(new BigDecimal("2500000")), "0.08"));
        } else {
            impuesto = new BigDecimal("120000").add(pct(base.subtract(new BigDecimal("3500000")), "0.135"));
        }
        return impuesto.setScale(2, RoundingMode.HALF_UP);
    }
}
