package com.rrhh.contratos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.config.TestJwtConfig;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.ContratoDocumento;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.repository.ContratoDocumentoRepository;
import com.rrhh.contratos.repository.ContratoRepository;
import com.rrhh.contratos.service.TrabajadoresClient;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MiContratoTest {
    private static final String TRABAJADOR = TestJwtConfig.TRABAJADOR_ID;
    private static final String JEFE = TestJwtConfig.JEFATURA_TRABAJADOR_ID;
    private static final String SIN_FICHA = "Tu usuario no está vinculado a una ficha de trabajador";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private ContratoDocumentoRepository documentoRepository;

    @MockBean
    private TrabajadoresClient trabajadoresClient;

    /** Los tokens de prueba apuntan siempre a las mismas fichas: cada test parte sin contratos para ellas. */
    @BeforeEach
    void limpiarFichasDePrueba() {
        String ids = "select id from contratos_contrato where trabajador_id in (?, ?)";
        jdbc.update("delete from contratos_documento where contrato_id in (select id from (" + ids + ") x)", TRABAJADOR, JEFE);
        jdbc.update("delete from contratos_contrato where trabajador_id in (?, ?)", TRABAJADOR, JEFE);
    }

    @Test
    void sinContratoDevuelveSinContrato() throws Exception {
        mvc.perform(get("/api/v1/contratos/mio").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("SIN_CONTRATO"))
                .andExpect(jsonPath("$.datos.contrato").doesNotExist());
        mvc.perform(get("/api/v1/contratos/mio/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mioSinTrabajadorIdEs403() throws Exception {
        MockHttpServletRequestBuilder[] llamadas = {
                get("/api/v1/contratos/mio"),
                put("/api/v1/contratos/mio/campos").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("campos", trabajadorValidos()))),
                post("/api/v1/contratos/mio/aprobar")
        };
        for (MockHttpServletRequestBuilder llamada : llamadas) {
            mvc.perform(llamada.header(HttpHeaders.AUTHORIZATION, "Bearer sin-ficha"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(SIN_FICHA));
        }
    }

    @Test
    void enEsperaMuestraCamposRrhh() throws Exception {
        emitir(TRABAJADOR);
        mio("trabajador")
                .andExpect(jsonPath("$.datos.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.datos.campos_rrhh.rrhh_sueldo_base").value("900000"))
                .andExpect(jsonPath("$.datos.documento_listo").value(false));
        mvc.perform(get("/api/v1/contratos/mio/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isConflict());
    }

    @Test
    void guardarCamposGeneraDocumentoYPreview() throws Exception {
        emitir(TRABAJADOR);
        guardarCampos("trabajador", trabajadorValidos())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.documento_listo").value(true))
                .andExpect(jsonPath("$.datos.campos_trabajador.trabajador_rut").value("12.345.678-9"));
        byte[] pdf = mvc.perform(get("/api/v1/contratos/mio/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"contrato-2026-12-01.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void camposInvalidosDelTrabajadorEs400() throws Exception {
        emitir(TRABAJADOR);
        Map<String, String> vacio = trabajadorValidos();
        vacio.put("trabajador_rut", "");
        guardarCampos("trabajador", vacio)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("trabajador_rut")));
        Map<String, String> conRrhh = trabajadorValidos();
        conRrhh.put("rrhh_cargo", "Gerente");
        guardarCampos("trabajador", conRrhh)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("rrhh_cargo")));
        mio("trabajador").andExpect(jsonPath("$.datos.documento_listo").value(false));
    }

    @Test
    void aprobarSinDocumentoEs409() throws Exception {
        emitir(TRABAJADOR);
        mvc.perform(post("/api/v1/contratos/mio/aprobar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isConflict());
        mio("trabajador").andExpect(jsonPath("$.datos.estado").value("EN_ESPERA"));
    }

    @Test
    void aprobarPasaAFirmado() throws Exception {
        String contratoId = emitir(TRABAJADOR);
        guardarCampos("trabajador", trabajadorValidos()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/contratos/mio/aprobar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.datos.contrato.activo").value(true))
                .andExpect(jsonPath("$.datos.documento_listo").value(true));

        Map<String, Object> fila = jdbc.queryForMap("select estado, activo, firmado_en from contratos_contrato where id = ?", contratoId);
        assertThat(fila.get("estado")).isEqualTo("FIRMADO");
        assertThat(fila.get("firmado_en")).isNotNull();
        byte[] guardado = jdbc.queryForObject("select pdf_firmado from contratos_documento where contrato_id = ?", byte[].class, contratoId);
        assertThat(guardado).isNotNull();
        assertThat(contar("select count(*) from contratos_audit_log where entidad_id = ? and accion = 'Firma'", contratoId)).isEqualTo(1);
        assertThat(contar("select count(*) from contratos_outbox_event where aggregate_id = ? and event_type = 'contrato.firmado'", contratoId)).isEqualTo(1);

        guardarCampos("trabajador", trabajadorValidos()).andExpect(status().isConflict());
        byte[] descargado = mvc.perform(get("/api/v1/contratos/mio/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(Arrays.equals(descargado, guardado)).isTrue();
    }

    @Test
    void rechazarVuelveASinContrato() throws Exception {
        String contratoId = emitir(TRABAJADOR);
        mvc.perform(post("/api/v1/contratos/mio/rechazar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Sueldo distinto al acordado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("SIN_CONTRATO"));

        assertThat(jdbc.queryForObject("select estado from contratos_contrato where id = ?", String.class, contratoId))
                .isEqualTo("RECHAZADO");
        assertThat(contar("select count(*) from contratos_audit_log where entidad_id = ? and accion = 'Rechazo' "
                + "and valores_nuevos = 'Sueldo distinto al acordado'", contratoId)).isEqualTo(1);
        assertThat(contar("select count(*) from contratos_outbox_event where aggregate_id = ? and event_type = 'contrato.rechazado'", contratoId)).isEqualTo(1);

        emitirRespuesta(TRABAJADOR).andExpect(status().isCreated());
    }

    @Test
    void rechazarSinCuerpoEsValido() throws Exception {
        emitir(TRABAJADOR);
        mvc.perform(post("/api/v1/contratos/mio/rechazar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("SIN_CONTRATO"));
    }

    @Test
    void jefaturaUsaElMismoFlujo() throws Exception {
        emitir(JEFE);
        mio("jefatura").andExpect(jsonPath("$.datos.estado").value("EN_ESPERA"));
    }

    @Test
    void trabajadorNoVeContratoDeOtro() throws Exception {
        emitir(JEFE);
        mio("trabajador").andExpect(jsonPath("$.datos.estado").value("SIN_CONTRATO"));
    }

    @Test
    void pdfDeContratoFirmadoSinDocumentoEs404() throws Exception {
        Contrato antiguo = contratoFirmadoSinDocumento(TestJwtConfig.TENANT_ID, TRABAJADOR);
        mio("trabajador")
                .andExpect(jsonPath("$.datos.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.datos.documento_listo").value(false));
        mvc.perform(get("/api/v1/contratos/mio/pdf").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El contrato firmado no tiene documento"));
        mvc.perform(get("/api/v1/contratos/{id}/pdf", antiguo.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El contrato firmado no tiene documento"));
    }

    @Test
    void adminObtienePdfFirmado() throws Exception {
        String contratoId = emitir(TRABAJADOR);
        guardarCampos("trabajador", trabajadorValidos()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/contratos/mio/aprobar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk());

        byte[] pdf = mvc.perform(get("/api/v1/contratos/{id}/pdf", contratoId).header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        Contrato ajeno = contratoFirmadoSinDocumento("ffffffff-ffff-ffff-ffff-ffffffffffff", UUID.randomUUID().toString());
        ContratoDocumento docAjeno = new ContratoDocumento();
        docAjeno.setId(UUID.randomUUID().toString());
        docAjeno.setTenantId(ajeno.getTenantId());
        docAjeno.setContratoId(ajeno.getId());
        docAjeno.setCamposRrhh("{}");
        docAjeno.setPdfFirmado(pdf);
        docAjeno.setPlantillaSha256("0".repeat(64));
        docAjeno.setActualizadoEn(Instant.now());
        documentoRepository.save(docAjeno);
        mvc.perform(get("/api/v1/contratos/{id}/pdf", ajeno.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/contratos/{id}/pdf", contratoId).header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listadoMarcaDocumentoFirmado() throws Exception {
        String firmadoId = emitir(TRABAJADOR);
        guardarCampos("trabajador", trabajadorValidos()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/contratos/mio/aprobar").header(HttpHeaders.AUTHORIZATION, "Bearer trabajador"))
                .andExpect(status().isOk());
        Contrato antiguo = contratoFirmadoSinDocumento(TestJwtConfig.TENANT_ID, JEFE);

        mvc.perform(get("/api/v1/contratos").header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id == '" + firmadoId + "')].documento_firmado").value(true))
                .andExpect(jsonPath("$.datos[?(@.id == '" + antiguo.getId() + "')].documento_firmado").value(false));
    }

    private String emitir(String trabajadorId) throws Exception {
        String body = emitirRespuesta(trabajadorId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("datos").path("id").asText();
    }

    private ResultActions emitirRespuesta(String trabajadorId) throws Exception {
        String body = json.writeValueAsString(Map.of("trabajador_id", trabajadorId, "campos", EmisionContratoTest.rrhhValidos()));
        return mvc.perform(post("/api/v1/contratos/emitir").header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions mio(String token) throws Exception {
        return mvc.perform(get("/api/v1/contratos/mio").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions guardarCampos(String token, Map<String, String> campos) throws Exception {
        return mvc.perform(put("/api/v1/contratos/mio/campos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("campos", campos))));
    }

    private int contar(String sql, String id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    /** Un contrato FIRMADO sin fila de documento, como los anteriores a V4 (jornada nula). */
    private Contrato contratoFirmadoSinDocumento(String tenantId, String trabajadorId) {
        Contrato c = new Contrato();
        c.setId(UUID.randomUUID().toString());
        c.setTenantId(tenantId);
        c.setTrabajadorId(trabajadorId);
        c.setSalarioBase(new BigDecimal("800000"));
        c.setTipoContrato("INDEFINIDO");
        c.setFechaInicio(LocalDate.of(2024, 3, 1));
        c.setEstado(EstadoContrato.FIRMADO);
        c.setActivo(true);
        c.setCreadoEn(Instant.now());
        return contratoRepository.save(c);
    }

    static Map<String, String> trabajadorValidos() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("trabajador_nombre_completo", "Ana Pérez Soto");
        v.put("trabajador_rut", "12.345.678-9");
        v.put("trabajador_nacionalidad", "Chilena");
        v.put("trabajador_fecha_nacimiento", "1990-05-10");
        v.put("trabajador_estado_civil", "Soltera");
        v.put("trabajador_domicilio", "Los Aromos 45");
        return v;
    }
}
