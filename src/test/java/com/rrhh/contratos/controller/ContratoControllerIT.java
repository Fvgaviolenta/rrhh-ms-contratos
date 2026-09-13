package com.rrhh.contratos.controller;

import com.rrhh.contratos.ContratosApplication;
import com.rrhh.contratos.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ContratosApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class ContratoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void creaContratoActivo() throws Exception {
        mockMvc.perform(post("/api/v1/contratos")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trabajador_id":"ffffffff-ffff-ffff-ffff-ffffffffffff",
                                  "salario_base":1000000,
                                  "tipo_contrato":"INDEFINIDO",
                                  "fecha_inicio":"2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.activo").value(true));
    }

    @Test
    void bloqueaSegundoContratoActivo() throws Exception {
        mockMvc.perform(post("/api/v1/contratos")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trabajador_id":"ffffffff-ffff-ffff-ffff-ffffffffffff",
                                  "salario_base":1200000,
                                  "tipo_contrato":"INDEFINIDO",
                                  "fecha_inicio":"2026-02-01"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(b -> b.subject("a")
                .claim("email", "admin.demo@rrhh.local")
                .claim("custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("custom:role", "Admin de RRHH")
                .claim("custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }
}
