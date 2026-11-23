package com.rrhh.contratos.dto.response;

import java.util.Map;

/**
 * Lo que ve el trabajador en "Mi contrato". {@code estado}: SIN_CONTRATO, EN_ESPERA o FIRMADO.
 * {@code documentoListo}: en espera, ya hay .docx para previsualizar; firmado, hay PDF guardado.
 */
public record MiContratoResponse(
        String estado,
        ContratoResponse contrato,
        Map<String, String> camposRrhh,
        Map<String, String> camposTrabajador,
        boolean documentoListo
) {
    public static final String SIN_CONTRATO = "SIN_CONTRATO";
    public static final String EN_ESPERA = "EN_ESPERA";
    public static final String FIRMADO = "FIRMADO";

    public static MiContratoResponse sinContrato() {
        return new MiContratoResponse(SIN_CONTRATO, null, null, null, false);
    }
}
