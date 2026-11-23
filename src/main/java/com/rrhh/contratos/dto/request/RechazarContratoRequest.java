package com.rrhh.contratos.dto.request;

import jakarta.validation.constraints.Size;

/** Motivo opcional del rechazo, hasta 500 caracteres. */
public record RechazarContratoRequest(@Size(max = 500) String motivo) {}
