package com.rrhh.contratos.controller;

import com.rrhh.contratos.exception.GlobalExceptionHandler;
import com.rrhh.contratos.service.ContratoService;
import com.rrhh.contratos.service.FlujoContratoService;
import com.rrhh.contratos.service.PlantillaContratoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Errores causados por el cliente: deben responder 4xx con el formato ApiResponse, no 500.
 * MockMvc standalone, sin contexto ni base: en main la migración V2 no corre sobre H2.
 */
class ErroresClienteTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ContratoController(mock(ContratoService.class), mock(FlujoContratoService.class),
                    mock(PlantillaContratoService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rutaSinControladorResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje").value("Recurso no encontrado"));
    }

    @Test
    void metodoNoPermitidoResponde405ConAllow() throws Exception {
        mockMvc.perform(delete("/api/v1/contratos"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.codigo").value(405))
                .andExpect(jsonPath("$.mensaje").value("Método no permitido"));
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/contratos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("Solicitud inválida"));
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mockMvc.perform(post("/api/v1/contratos")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value(415));
    }

    @Test
    void validacionFallidaMantieneLosErroresPorCampo() throws Exception {
        mockMvc.perform(post("/api/v1/contratos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Validación fallida"))
                .andExpect(jsonPath("$.errores").isNotEmpty());
    }
}
