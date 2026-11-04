package com.rrhh.contratos.util;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlantillaDocxTest {

    @Test
    void extraeClavesDeParrafosTablasYPie() {
        List<String> claves = PlantillaDocx.extraerClaves(plantilla());
        assertThat(claves).contains("trabajador_rut", "rrhh_empresa_razon_social", "trabajador_nombre_completo");
        assertThat(claves).doesNotHaveDuplicates().hasSize(18);
    }

    @Test
    void rellenaClavePartidaEnVariosRuns() {
        String texto = textoDe(PlantillaDocx.rellenar(plantilla(), valoresCompletos()));
        assertThat(texto).contains("Ana Pérez Soto").doesNotContain("$trabajador_").doesNotContain("nombre_completo");
    }

    @Test
    void valorConDolarQuedaLiteral() {
        var v = valoresCompletos();
        v.put("trabajador_domicilio", "Calle $rrhh_sueldo_base 12");
        assertThat(textoDe(PlantillaDocx.rellenar(plantilla(), v))).contains("Calle $rrhh_sueldo_base 12");
    }

    @Test
    void faltaUnValorLanza() {
        var v = valoresCompletos();
        v.remove("trabajador_rut");
        assertThatThrownBy(() -> PlantillaDocx.rellenar(plantilla(), v)).hasMessage("Campo sin valor: trabajador_rut");
    }

    @Test
    void convierteAPdf() {
        byte[] pdf = PlantillaDocx.aPdf(PlantillaDocx.rellenar(plantilla(), valoresCompletos()));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    static byte[] plantilla() {
        try (InputStream in = PlantillaDocxTest.class.getResourceAsStream("/plantillas/contrato-estandar.docx")) {
            assertThat(in).as("plantilla en el classpath").isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> valoresCompletos() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("rrhh_ciudad_firma", "Santiago");
        v.put("rrhh_empresa_razon_social", "Empresa Demo SpA");
        v.put("rrhh_empresa_rut", "76.123.456-7");
        v.put("rrhh_representante_legal", "Juan Soto");
        v.put("rrhh_empresa_domicilio", "Av. Siempre Viva 123");
        v.put("trabajador_nombre_completo", "Ana Pérez Soto");
        v.put("trabajador_rut", "12.345.678-9");
        v.put("trabajador_nacionalidad", "Chilena");
        v.put("trabajador_fecha_nacimiento", "1990-05-10");
        v.put("trabajador_estado_civil", "Soltera");
        v.put("trabajador_domicilio", "Los Aromos 45");
        v.put("rrhh_cargo", "Analista");
        v.put("rrhh_lugar_trabajo", "Casa matriz");
        v.put("rrhh_horas_semanales", "42");
        v.put("rrhh_sueldo_base", "900000");
        v.put("rrhh_tipo_contrato", "INDEFINIDO");
        v.put("rrhh_fecha_inicio", "2026-12-01");
        v.put("rrhh_fecha_termino", "Indefinido");
        return v;
    }

    /** Texto de parrafos, tablas (recursivas) y pies, una linea por parrafo. */
    public static String textoDe(byte[] docx) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder sb = new StringBuilder();
            agregar(doc.getBodyElements(), sb);
            for (XWPFFooter footer : doc.getFooterList()) {
                agregar(footer.getBodyElements(), sb);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void agregar(List<IBodyElement> elementos, StringBuilder sb) {
        for (IBodyElement e : elementos) {
            if (e instanceof XWPFParagraph p) {
                sb.append(p.getText()).append('\n');
            } else if (e instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        agregar(cell.getBodyElements(), sb);
                    }
                }
            }
        }
    }
}
