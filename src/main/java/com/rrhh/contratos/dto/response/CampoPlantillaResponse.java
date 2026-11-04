package com.rrhh.contratos.dto.response;

import com.rrhh.contratos.service.PlantillaContratoService.Responsable;
import com.rrhh.contratos.service.PlantillaContratoService.TipoCampo;

import java.util.List;

/** Un campo de la plantilla tal como lo pinta el formulario. {@code opciones} solo trae valores en tipo OPCION. */
public record CampoPlantillaResponse(
        String clave,
        String etiqueta,
        Responsable responsable,
        TipoCampo tipo,
        List<String> opciones,
        boolean obligatorio
) {}
