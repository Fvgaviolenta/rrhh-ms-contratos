package com.rrhh.contratos.controller;

import com.rrhh.contratos.config.TestJwtConfig;
import com.rrhh.contratos.security.TenantEstadoCache;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S6: 403 a todo token de una empresa SUSPENDIDO, incluso emitido antes de suspender. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class TenantSuspendidoIT {

    private static final String SUSPENDIDA = "La empresa está suspendida";
    private static final String RUTA = "/api/v1/contratos";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantEstadoCache tenantEstadoCache;

    @BeforeEach
    void empresaActiva() {
        jdbc.update("DELETE FROM identity_tenant WHERE id = ?", TestJwtConfig.TENANT_SUSPENDIBLE);
        jdbc.update("INSERT INTO identity_tenant (id, slug, estado) VALUES (?, 'empresa-suspendible', 'ACTIVO')",
                TestJwtConfig.TENANT_SUSPENDIBLE);
        tenantEstadoCache.invalidar(TestJwtConfig.TENANT_SUSPENDIBLE);
    }

    private void suspender() {
        jdbc.update("UPDATE identity_tenant SET estado = 'SUSPENDIDO' WHERE id = ?", TestJwtConfig.TENANT_SUSPENDIBLE);
        tenantEstadoCache.invalidar(TestJwtConfig.TENANT_SUSPENDIBLE);
    }

    private ResultActions pedir(String token) throws Exception {
        return mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token));
    }

    @Test
    void empresaActivaPasa() throws Exception {
        pedir("admin-suspendida").andExpect(status().isOk());
    }

    @Test
    void tokenPrevioDeEmpresaSuspendidaRecibe403() throws Exception {
        pedir("admin-suspendida");
        suspender();
        pedir("admin-suspendida")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403))
                .andExpect(jsonPath("$.mensaje").value(SUSPENDIDA));
    }

    @Test
    void tenantSinFilaEnIdentityNoSeBloquea() throws Exception {
        jdbc.update("DELETE FROM identity_tenant WHERE id = ?", TestJwtConfig.TENANT_SUSPENDIBLE);
        tenantEstadoCache.invalidar(TestJwtConfig.TENANT_SUSPENDIBLE);
        assertNotEquals(403, pedir("admin-suspendida").andReturn().getResponse().getStatus());
    }

    @Test
    void rolDePlataformaNoEsBloqueado() throws Exception {
        suspender();
        var respuesta = pedir("admin-plataforma-con-tenant").andReturn().getResponse();
        assertFalse(respuesta.getStatus() == 403 && respuesta.getContentAsString().contains(SUSPENDIDA));
    }
}
