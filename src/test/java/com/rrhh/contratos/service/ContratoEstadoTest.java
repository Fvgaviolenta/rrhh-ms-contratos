package com.rrhh.contratos.service;

import com.rrhh.contratos.config.TestJwtConfig;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.repository.ContratoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class ContratoEstadoTest {
    private static final String ADMIN = "Bearer admin";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ContratoRepository contratoRepository;

    @Test
    void finalizarPendienteEs409() throws Exception {
        Contrato c = contrato(EstadoContrato.PENDIENTE_FIRMA);
        mvc.perform(post("/api/v1/contratos/{id}/finalizar", c.getId()).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("PENDIENTE_FIRMA")));
    }

    @Test
    void finalizarFirmadoPasaAFinalizado() throws Exception {
        Contrato c = contrato(EstadoContrato.FIRMADO);
        mvc.perform(post("/api/v1/contratos/{id}/finalizar", c.getId()).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.datos.activo").value(false));
    }

    @Test
    void patchSobrePendienteEs409() throws Exception {
        Contrato c = contrato(EstadoContrato.PENDIENTE_FIRMA);
        mvc.perform(patch("/api/v1/contratos/{id}", c.getId()).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"salario_base\": 1000000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("PENDIENTE_FIRMA")));
    }

    @Test
    void patchSobreRechazadoEs409() throws Exception {
        Contrato c = contrato(EstadoContrato.RECHAZADO);
        mvc.perform(patch("/api/v1/contratos/{id}", c.getId()).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"salario_base\": 1000000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("RECHAZADO")));
    }

    @Test
    void liquidacionSobrePendienteEs409() throws Exception {
        Contrato c = contrato(EstadoContrato.PENDIENTE_FIRMA);
        mvc.perform(post("/api/v1/liquidaciones/calcular").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrato_id\": \"" + c.getId() + "\", \"periodo\": \"2026-11\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("PENDIENTE_FIRMA")));
    }

    @Test
    void respuestaIncluyeEstadoYJornada() throws Exception {
        Contrato c = contrato(EstadoContrato.FIRMADO);
        mvc.perform(get("/api/v1/contratos/{id}", c.getId()).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.datos.jornada_horas_semanales").value(42));
    }

    private Contrato contrato(EstadoContrato estado) {
        Contrato c = new Contrato();
        c.setId(UUID.randomUUID().toString());
        c.setTenantId(TestJwtConfig.TENANT_ID);
        c.setTrabajadorId(UUID.randomUUID().toString());
        c.setSalarioBase(new BigDecimal("900000"));
        c.setTipoContrato("INDEFINIDO");
        c.setFechaInicio(LocalDate.of(2026, 1, 1));
        c.setJornadaHorasSemanales(42);
        c.setEstado(estado);
        c.setActivo(estado == EstadoContrato.FIRMADO);
        c.setCreadoEn(Instant.now());
        return contratoRepository.save(c);
    }
}
