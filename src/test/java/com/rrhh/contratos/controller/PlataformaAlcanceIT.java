package com.rrhh.contratos.controller;

import com.rrhh.contratos.config.TestJwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S3: alcance de AdminPlataforma y OperadorSaaS en la ruta de plataforma; fuera de alcance, 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class PlataformaAlcanceIT {

    private static final String A = "a1a1a1a1-0000-4000-8000-00000000000a";
    private static final String B = "b2b2b2b2-0000-4000-8000-00000000000b";
    private static final String PLATAFORMA = "00000000-0000-0000-0000-000000000001";
    private static final String GRUPO = "c3c3c3c3-0000-4000-8000-00000000000c";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void datos() {
        empresa(A, "alcance-a");
        empresa(B, "alcance-b");
        empresa(PLATAFORMA, "plataforma");
        jdbc.update("DELETE FROM identity_grupo_tenant WHERE grupo_id = ?", GRUPO);
        jdbc.update("DELETE FROM identity_grupo_operador WHERE grupo_id = ?", GRUPO);
        jdbc.update("INSERT INTO identity_grupo_tenant (grupo_id, tenant_id) VALUES (?, ?)", GRUPO, A);
        jdbc.update("INSERT INTO identity_grupo_operador (grupo_id, usuario_id) VALUES (?, ?)", GRUPO, TestJwtConfig.OPERADOR_GRUPO_ID);
        jdbc.update("DELETE FROM identity_usuario WHERE id = ?", TestJwtConfig.OPERADOR_GRUPO_ID);
        jdbc.update("INSERT INTO identity_usuario (id, cognito_sub, rol) VALUES (?, ?, 'OperadorSaaS')",
                TestJwtConfig.OPERADOR_GRUPO_ID, TestJwtConfig.OPERADOR_SIN_CLAIM_SUB);
    }

    private void empresa(String id, String slug) {
        jdbc.update("DELETE FROM identity_tenant WHERE id = ?", id);
        jdbc.update("INSERT INTO identity_tenant (id, slug, estado) VALUES (?, ?, 'ACTIVO')", id, slug);
    }

    private ResultActions pedir(String token, String tenantId) throws Exception {
        return mockMvc.perform(get("/api/v1/plataforma/tenants/{tenantId}/contratos", tenantId).header("Authorization", "Bearer " + token));
    }

    @Test
    void operadorVeSuEmpresaYRecibe404FueraDeSuGrupo() throws Exception {
        pedir("operador-grupo", A).andExpect(status().isOk());
        pedir("operador-grupo", B).andExpect(status().isNotFound());
    }

    @Test
    void operadorSinGrupoRecibe404() throws Exception {
        pedir("operador-sin-grupo", A).andExpect(status().isNotFound());
    }

    @Test
    void operadorSinClaimUserIdSeResuelvePorSuCognitoSub() throws Exception {
        pedir("operador-grupo-sin-claim", A).andExpect(status().isOk());
        pedir("operador-grupo-sin-claim", B).andExpect(status().isNotFound());
    }

    @Test
    void adminPlataformaVeCualquierEmpresaCliente() throws Exception {
        pedir("admin-plataforma", B).andExpect(status().isOk());
    }

    @Test
    void adminPlataformaNoAlcanzaLaEmpresaPlataforma() throws Exception {
        pedir("admin-plataforma", PLATAFORMA).andExpect(status().isNotFound());
        pedir("admin-plataforma", "no-existe").andExpect(status().isNotFound());
    }

    @Test
    void rolDeEmpresaNoUsaLaRutaDePlataforma() throws Exception {
        pedir("admin", A).andExpect(status().isForbidden());
    }
}
