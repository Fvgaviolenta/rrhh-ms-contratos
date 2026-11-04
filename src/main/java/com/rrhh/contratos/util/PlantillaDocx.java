package com.rrhh.contratos.util;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operaciones sin estado sobre una plantilla .docx con campos {@code $clave}: extraer claves, rellenar y
 * convertir a PDF. Los campos se buscan en parrafos del cuerpo, celdas de tabla (recursivas), encabezados y
 * pies. El reemplazo trabaja por parrafo para cubrir un campo que Word partio en varios runs.
 */
public final class PlantillaDocx {
    public static final Pattern CAMPO = Pattern.compile("\\$([a-z][a-z0-9_]*)");

    private PlantillaDocx() {}

    /** Claves de la plantilla, en orden de aparicion, sin duplicados y sin el {@code $}. */
    public static List<String> extraerClaves(byte[] docx) {
        Set<String> claves = new LinkedHashSet<>();
        try (XWPFDocument doc = abrir(docx)) {
            recorrerParrafos(doc, p -> {
                Matcher m = CAMPO.matcher(textoDe(p));
                while (m.find()) {
                    claves.add(m.group(1));
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible leer la plantilla .docx", e);
        }
        return List.copyOf(claves);
    }

    /**
     * Reemplaza cada {@code $clave} por su valor en una sola pasada (un valor que contiene {@code $} queda
     * literal). Lanza {@code IllegalStateException("Campo sin valor: <clave>")} si falta el valor de una
     * clave de la plantilla.
     */
    public static byte[] rellenar(byte[] docx, Map<String, String> valores) {
        for (String clave : extraerClaves(docx)) {
            if (valores.get(clave) == null) {
                throw new IllegalStateException("Campo sin valor: " + clave);
            }
        }
        try (XWPFDocument doc = abrir(docx); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            recorrerParrafos(doc, p -> reemplazar(p, valores));
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible generar el documento .docx", e);
        }
    }

    /** Convierte un .docx a PDF con xdocreport. */
    public static byte[] aPdf(byte[] docx) {
        try (XWPFDocument doc = abrir(docx); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfConverter.getInstance().convert(doc, out, PdfOptions.create());
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible convertir el documento a PDF", e);
        }
    }

    private static XWPFDocument abrir(byte[] docx) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(docx));
    }

    private static void reemplazar(XWPFParagraph p, Map<String, String> valores) {
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            return;
        }
        String texto = textoDe(p);
        Matcher m = CAMPO.matcher(texto);
        if (!m.find()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException("Campo sin valor: " + m.group(1));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(valor));
        }
        m.appendTail(sb);
        for (int i = runs.size() - 1; i >= 1; i--) {
            vaciar(runs.get(i));
        }
        XWPFRun primero = runs.get(0);
        vaciar(primero);
        primero.setText(sb.toString(), 0);
    }

    private static void vaciar(XWPFRun run) {
        while (run.getCTR().sizeOfTArray() > 0) {
            run.getCTR().removeT(0);
        }
    }

    /** Texto de los elementos {@code w:t} de los runs; tabs y saltos quedan en su run, fuera del reemplazo. */
    private static String textoDe(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            for (CTText t : run.getCTR().getTList()) {
                if (t.getStringValue() != null) {
                    sb.append(t.getStringValue());
                }
            }
        }
        return sb.toString();
    }

    private static void recorrerParrafos(XWPFDocument doc, Consumer<XWPFParagraph> accion) {
        List<List<IBodyElement>> bloques = new ArrayList<>();
        bloques.add(doc.getBodyElements());
        for (XWPFHeader header : doc.getHeaderList()) {
            bloques.add(header.getBodyElements());
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            bloques.add(footer.getBodyElements());
        }
        for (List<IBodyElement> bloque : bloques) {
            recorrer(bloque, accion);
        }
    }

    private static void recorrer(List<IBodyElement> elementos, Consumer<XWPFParagraph> accion) {
        for (IBodyElement e : elementos) {
            if (e instanceof XWPFParagraph p) {
                accion.accept(p);
            } else if (e instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        recorrer(cell.getBodyElements(), accion);
                    }
                }
            }
        }
    }
}
