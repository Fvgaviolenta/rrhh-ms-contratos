package com.rrhh.contratos.service;

import com.rrhh.contratos.dto.ApiResponse;
import com.rrhh.contratos.dto.response.CampoPlantillaResponse;
import com.rrhh.contratos.exception.ValidacionCamposException;
import com.rrhh.contratos.service.PlantillaContratoService.Responsable;
import com.rrhh.contratos.service.PlantillaContratoService.TipoCampo;
import com.rrhh.contratos.util.PlantillaDocxTest;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlantillaContratoServiceTest {

    private final PlantillaContratoService servicio = new PlantillaContratoService(plantilla());

    @Test
    void camposTienenResponsableTipoYEtiqueta() {
        Map<String, CampoPlantillaResponse> porClave = new HashMap<>();
        servicio.campos().forEach(c -> porClave.put(c.clave(), c));

        CampoPlantillaResponse horas = porClave.get("rrhh_horas_semanales");
        assertThat(horas.responsable()).isEqualTo(Responsable.RRHH);
        assertThat(horas.tipo()).isEqualTo(TipoCampo.ENTERO);
        assertThat(horas.obligatorio()).isTrue();

        CampoPlantillaResponse tipo = porClave.get("rrhh_tipo_contrato");
        assertThat(tipo.tipo()).isEqualTo(TipoCampo.OPCION);
        assertThat(tipo.opciones()).containsExactly("INDEFINIDO", "PLAZO_FIJO", "HONORARIOS");

        CampoPlantillaResponse termino = porClave.get("rrhh_fecha_termino");
        assertThat(termino.tipo()).isEqualTo(TipoCampo.FECHA);
        assertThat(termino.obligatorio()).isFalse();

        CampoPlantillaResponse nombre = porClave.get("trabajador_nombre_completo");
        assertThat(nombre.responsable()).isEqualTo(Responsable.TRABAJADOR);
        assertThat(nombre.tipo()).isEqualTo(TipoCampo.TEXTO);
        assertThat(nombre.etiqueta()).isEqualTo("Nombre completo");

        assertThat(servicio.sha256()).hasSize(64);
    }

    @Test
    void prefijoInvalidoFallaAlArrancar() {
        byte[] docx = docxCon("$rrhh_sueldo_base $rrhh_horas_semanales $rrhh_tipo_contrato $rrhh_fecha_inicio $empresa_nombre");
        assertThatThrownBy(() -> new PlantillaContratoService(docx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empresa_nombre");
    }

    @Test
    void faltaClaveTipadaFallaAlArrancar() {
        byte[] docx = docxCon("$rrhh_sueldo_base $rrhh_tipo_contrato $rrhh_fecha_inicio $trabajador_rut");
        assertThatThrownBy(() -> new PlantillaContratoService(docx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rrhh_horas_semanales");
    }

    @Test
    void sueldoConPuntoDeMilesEsInvalido() {
        for (String sueldo : List.of("900.000", "0", "abc")) {
            Map<String, String> v = rrhhValidos();
            v.put("rrhh_sueldo_base", sueldo);
            assertThat(camposConError(Responsable.RRHH, v)).as(sueldo).containsExactly("rrhh_sueldo_base");
        }
    }

    @Test
    void horasFueraDeRango() {
        for (String horas : List.of("0", "43")) {
            Map<String, String> v = rrhhValidos();
            v.put("rrhh_horas_semanales", horas);
            assertThat(camposConError(Responsable.RRHH, v)).as(horas).containsExactly("rrhh_horas_semanales");
        }
        Map<String, String> v = rrhhValidos();
        v.put("rrhh_horas_semanales", "42");
        assertThat(servicio.validar(Responsable.RRHH, v)).containsEntry("rrhh_horas_semanales", "42");
    }

    @Test
    void plazoFijoSinTerminoEsInvalido() {
        Map<String, String> v = rrhhValidos();
        v.put("rrhh_tipo_contrato", "PLAZO_FIJO");
        assertThat(camposConError(Responsable.RRHH, v)).containsExactly("rrhh_fecha_termino");
    }

    @Test
    void terminoAntesDeInicioEsInvalido() {
        Map<String, String> v = rrhhValidos();
        v.put("rrhh_fecha_termino", "2026-11-30");
        assertThat(camposConError(Responsable.RRHH, v)).containsExactly("rrhh_fecha_termino");
    }

    @Test
    void fechaTerminoVaciaEnIndefinidoEsValida() {
        Map<String, String> v = rrhhValidos();
        v.put("rrhh_fecha_termino", "");
        Map<String, String> normalizados = servicio.validar(Responsable.RRHH, v);
        assertThat(normalizados).doesNotContainKey("rrhh_fecha_termino");
        assertThat(normalizados).containsEntry("rrhh_sueldo_base", "900000");
    }

    @Test
    void textoVacioOMayorA500EsInvalido() {
        Map<String, String> vacio = trabajadorValidos();
        vacio.put("trabajador_rut", "");
        assertThat(camposConError(Responsable.TRABAJADOR, vacio)).containsExactly("trabajador_rut");

        Map<String, String> largo = trabajadorValidos();
        largo.put("trabajador_domicilio", "x".repeat(501));
        assertThat(camposConError(Responsable.TRABAJADOR, largo)).containsExactly("trabajador_domicilio");

        Map<String, String> limite = trabajadorValidos();
        limite.put("trabajador_domicilio", "x".repeat(500));
        assertThatCode(() -> servicio.validar(Responsable.TRABAJADOR, limite)).doesNotThrowAnyException();
    }

    @Test
    void campoDeOtroResponsableEsInvalido() {
        Map<String, String> v = rrhhValidos();
        v.put("trabajador_rut", "1-9");
        assertThat(camposConError(Responsable.RRHH, v)).containsExactly("trabajador_rut");
    }

    @Test
    void todasLasViolacionesVanJuntas() {
        Map<String, String> v = rrhhValidos();
        v.put("rrhh_sueldo_base", "0");
        v.put("rrhh_horas_semanales", "50");
        v.remove("rrhh_cargo");
        assertThat(camposConError(Responsable.RRHH, v))
                .containsExactlyInAnyOrder("rrhh_sueldo_base", "rrhh_horas_semanales", "rrhh_cargo");
    }

    @Test
    void rellenarSinFechaTerminoEscribeIndefinido() {
        Map<String, String> rrhh = servicio.validar(Responsable.RRHH, rrhhValidos());
        Map<String, String> trabajador = servicio.validar(Responsable.TRABAJADOR, trabajadorValidos());
        String texto = PlantillaDocxTest.textoDe(servicio.generarDocx(rrhh, trabajador));
        assertThat(texto).contains("Indefinido").doesNotContain("$rrhh_fecha_termino").doesNotContain("$");
    }

    private List<String> camposConError(Responsable r, Map<String, String> valores) {
        ValidacionCamposException ex = catchThrowableOfType(() -> servicio.validar(r, valores), ValidacionCamposException.class);
        assertThat(ex).as("se esperaba ValidacionCamposException").isNotNull();
        return ex.getErrores().stream().map(ApiResponse.ErrorItem::campo).toList();
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

    static byte[] plantilla() {
        try (InputStream in = PlantillaContratoServiceTest.class.getResourceAsStream("/plantillas/contrato-estandar.docx")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] docxCon(String texto) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(texto);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
