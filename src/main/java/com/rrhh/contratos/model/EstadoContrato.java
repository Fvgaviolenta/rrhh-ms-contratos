package com.rrhh.contratos.model;

/**
 * Estado del contrato (S2). Transiciones: emitir (nuevo a PENDIENTE_FIRMA), aprobar (PENDIENTE_FIRMA a
 * FIRMADO), rechazar (PENDIENTE_FIRMA a RECHAZADO) y finalizar (FIRMADO a FINALIZADO). {@code activo} es
 * true si y solo si el estado es FIRMADO.
 */
public enum EstadoContrato {
    PENDIENTE_FIRMA,
    FIRMADO,
    RECHAZADO,
    FINALIZADO
}
