package com.rrhh.contratos.controller;

import com.rrhh.contratos.ContratosApplication;
import com.rrhh.contratos.config.TestJwtConfig;
import com.rrhh.contratos.service.TrabajadoresClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ContratosApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
// El token "admin" lo resuelve TestJwtConfig y pasa por JwtTenantConverter, como en producción.
class ContratoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    // Evita la llamada HTTP real a rrhh-trabajadores (localhost:8082): el mock acepta cualquier trabajador.
    @MockBean
    private TrabajadoresClient trabajadoresClient;

    @Test
    void creaContratoActivo() throws Exception {
        crearContrato(UUID.randomUUID().toString(), "2026-01-01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.activo").value(true));
    }

    @Test
    void bloqueaSegundoContratoActivo() throws Exception {
        // Trabajador propio del test, para no depender del orden ni de los datos de otras clases.
        String trabajadorId = UUID.randomUUID().toString();
        crearContrato(trabajadorId, "2026-01-01").andExpect(status().isCreated());
        crearContrato(trabajadorId, "2026-02-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El trabajador ya tiene un contrato activo"));
    }

    private ResultActions crearContrato(String trabajadorId, String fechaInicio) throws Exception {
        return mockMvc.perform(post("/api/v1/contratos")
                .header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "trabajador_id":"%s",
                          "salario_base":1000000,
                          "tipo_contrato":"INDEFINIDO",
                          "fecha_inicio":"%s"
                        }
                        """.formatted(trabajadorId, fechaInicio)));
    }
}
