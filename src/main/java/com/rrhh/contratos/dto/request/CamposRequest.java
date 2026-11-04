package com.rrhh.contratos.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Campos {@code trabajador_*} que completa el propio trabajador. */
public record CamposRequest(@NotNull Map<String, String> campos) {}
