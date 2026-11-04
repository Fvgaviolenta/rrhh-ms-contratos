package com.rrhh.contratos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.config.TestJwtConfig;
import com.rrhh.contratos.exception.DomainException;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.repository.ContratoRepository;
import com.rrhh.contratos.service.TrabajadoresClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class EmisionContratoTest {
    private static final String TRABAJADOR = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContratoRepository contratoRepository;

    @MockBean
    private TrabajadoresClient trabajadoresClient;

    @Test
    void camposDeLaPlantillaParaAdmin() throws Exception {
        mvc.perform(get("/api/v1/contratos/plantilla/campos").header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos", hasSize(18)))
                .andExpect(jsonPath("$.datos[?(@.clave == 'rrhh_sueldo_base')].tipo").value("NUMERO"));
        mvc.perform(get("/api/v1/contratos/plantilla/campos").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos", hasSize(18)));
    }

    @Test
    void emiteContratoPendiente() throws Exception {
        String body = emitir("admin", TRABAJADOR, rrhhValidos())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE_FIRMA"))
                .andExpect(jsonPath("$.datos.activo").value(false))
                .andExpect(jsonPath("$.datos.salario_base").value(900000))
                .andExpect(jsonPath("$.datos.jornada_horas_semanales").value(42))
                .andReturn().getResponse().getContentAsString();
        String contratoId = json.readTree(body).path("datos").path("id").asText();

        Map<String, Object> doc = jdbc.queryForMap(
                "select docx, plantilla_sha256, campos_rrhh from contratos_documento where contrato_id = ?", contratoId);
        assertThat(doc.get("docx")).isNull();
        assertThat((String) doc.get("plantilla_sha256")).hasSize(64);
        assertThat(json.readTree((String) doc.get("campos_rrhh")).path("rrhh_sueldo_base").asText()).isEqualTo("900000");

        Integer auditorias = jdbc.queryForObject(
                "select count(*) from contratos_audit_log where entidad_id = ? and accion = 'Emision' and valores_nuevos = '900000'",
                Integer.class, contratoId);
        assertThat(auditorias).isEqualTo(1);

        String payload = jdbc.queryForObject(
                "select payload from contratos_outbox_event where aggregate_id = ? and event_type = 'contrato.creado'",
                String.class, contratoId);
        assertThat(json.readTree(payload).path("trabajadorId").asText()).isEqualTo(TRABAJADOR);
        assertThat(json.readTree(payload).path("contratoId").asText()).isEqualTo(contratoId);
    }

    @Test
    void emitirATrabajadorEnEsperaEs409() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        emitir("admin", trabajador, rrhhValidos()).andExpect(status().isCreated());
        emitir("admin", trabajador, rrhhValidos())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("PENDIENTE_FIRMA")));
        assertThat(contratosDe(trabajador)).isEqualTo(1);
    }

    @Test
    void emitirATrabajadorFirmadoEs409() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        Contrato firmado = new Contrato();
        firmado.setId(UUID.randomUUID().toString());
        firmado.setTenantId(TestJwtConfig.TENANT_ID);
        firmado.setTrabajadorId(trabajador);
        firmado.setSalarioBase(new BigDecimal("800000"));
        firmado.setTipoContrato("INDEFINIDO");
        firmado.setFechaInicio(LocalDate.of(2025, 1, 1));
        firmado.setEstado(EstadoContrato.FIRMADO);
        firmado.setActivo(true);
        firmado.setCreadoEn(Instant.now());
        contratoRepository.save(firmado);

        emitir("admin", trabajador, rrhhValidos())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("FIRMADO")));
        assertThat(contratosDe(trabajador)).isEqualTo(1);
    }

    @Test
    void emitirConCamposInvalidosEs400ConErrores() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        Map<String, String> campos = rrhhValidos();
        campos.put("rrhh_sueldo_base", "0");
        campos.put("rrhh_horas_semanales", "50");
        emitir("admin", trabajador, campos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Validación fallida"))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("rrhh_sueldo_base", "rrhh_horas_semanales")));
        assertThat(contratosDe(trabajador)).isZero();
    }

    @Test
    void emitirConCampoDeTrabajadorEs400() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        Map<String, String> campos = rrhhValidos();
        campos.put("trabajador_rut", "1-9");
        emitir("admin", trabajador, campos)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("trabajador_rut")));
        assertThat(contratosDe(trabajador)).isZero();
    }

    @Test
    void trabajadorNoPuedeEmitir() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        emitir("trabajador", trabajador, rrhhValidos()).andExpect(status().isForbidden());
        assertThat(contratosDe(trabajador)).isZero();
    }

    @Test
    void trabajadorDeOtroTenantEs400() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        doThrow(new DomainException(400, "El trabajador no pertenece al tenant o no existe", "trabajador_id"))
                .when(trabajadoresClient).exigirDelTenant(trabajador);
        emitir("admin", trabajador, rrhhValidos())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("trabajador_id"));
        assertThat(contratosDe(trabajador)).isZero();
    }

    @Test
    void emisionesConcurrentesCreanUnSoloContrato() throws Exception {
        String trabajador = UUID.randomUUID().toString();
        // Las dos solicitudes pasan juntas la validacion del trabajador y luego compiten por crear el contrato.
        CyclicBarrier juntas = new CyclicBarrier(2);
        doAnswer(inv -> {
            juntas.await(10, TimeUnit.SECONDS);
            return null;
        }).when(trabajadoresClient).exigirDelTenant(trabajador);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> emitir("admin", trabajador, rrhhValidos()).andReturn().getResponse().getStatus());
            Future<Integer> b = pool.submit(() -> emitir("admin", trabajador, rrhhValidos()).andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(contratosDe(trabajador)).isEqualTo(1);
    }

    private ResultActions emitir(String token, String trabajadorId, Map<String, String> campos) throws Exception {
        String body = json.writeValueAsString(Map.of("trabajador_id", trabajadorId, "campos", campos));
        return mvc.perform(post("/api/v1/contratos/emitir").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int contratosDe(String trabajadorId) {
        return jdbc.queryForObject("select count(*) from contratos_contrato where trabajador_id = ?", Integer.class, trabajadorId);
    }

    static Map<String, String> rrhhValidos() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("rrhh_ciudad_firma", "Santiago");
        v.put("rrhh_empresa_razon_social", "Empresa Demo SpA");
        v.put("rrhh_empresa_rut", "76.123.456-7");
        v.put("rrhh_representante_legal", "Juan Soto");
        v.put("rrhh_empresa_domicilio", "Av. Siempre Viva 123");
        v.put("rrhh_cargo", "Analista");
        v.put("rrhh_lugar_trabajo", "Casa matriz");
        v.put("rrhh_horas_semanales", "42");
        v.put("rrhh_sueldo_base", "900000");
        v.put("rrhh_tipo_contrato", "INDEFINIDO");
        v.put("rrhh_fecha_inicio", "2026-12-01");
        return v;
    }
}
