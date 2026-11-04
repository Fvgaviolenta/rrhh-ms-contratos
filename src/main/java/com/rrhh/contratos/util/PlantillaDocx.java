package com.rrhh.contratos.util;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
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
 * convertir a PDF. Los campos se buscan en todo parrafo ({@code w:p}) del cuerpo, encabezados, pies, notas al
 * pie y notas finales, a cualquier profundidad: tablas, controles de contenido y cajas de texto incluidos.
 * El reemplazo trabaja por parrafo sobre sus textos ({@code w:t}) para cubrir un campo que Word partio en
 * varios runs: solo se tocan los textos que abarca cada campo, y el valor toma el formato del primero.
 */
public final class PlantillaDocx {
    public static final Pattern CAMPO = Pattern.compile("\\$([a-z][a-z0-9_]*)");

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS = "declare namespace w='" + W + "' ";
    private static final QName PARRAFO = new QName(W, "p");
    private static final QName XML_SPACE = new QName("http://www.w3.org/XML/1998/namespace", "space");

    private PlantillaDocx() {}

    /** Claves de la plantilla, en orden de aparicion, sin duplicados y sin el {@code $}. */
    public static List<String> extraerClaves(byte[] docx) {
        Set<String> claves = new LinkedHashSet<>();
        try (XWPFDocument doc = abrir(docx)) {
            recorrerParrafos(doc, textos -> {
                Matcher m = CAMPO.matcher(unir(textos));
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
            recorrerParrafos(doc, textos -> reemplazar(textos, valores));
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

    /**
     * Reemplaza los campos de un parrafo. Los matches se aplican de derecha a izquierda sobre los textos
     * originales: el texto del primer {@code w:t} que abarca un campo queda con su prefijo, el valor y (si el
     * campo termina en el mismo {@code w:t}) su sufijo; los {@code w:t} intermedios quedan vacios y el ultimo
     * conserva lo que sigue al campo. Tabs, saltos y formato de los demas runs no se tocan.
     */
    private static void reemplazar(List<XmlObject> textos, Map<String, String> valores) {
        List<String> partes = new ArrayList<>();
        for (XmlObject t : textos) {
            partes.add(leer(t));
        }
        String completo = String.join("", partes);
        Matcher m = CAMPO.matcher(completo);
        List<int[]> matches = new ArrayList<>();
        List<String> reemplazos = new ArrayList<>();
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException("Campo sin valor: " + m.group(1));
            }
            matches.add(new int[]{m.start(), m.end()});
            reemplazos.add(valor);
        }
        if (matches.isEmpty()) {
            return;
        }
        int[] inicios = new int[partes.size()];
        for (int i = 1; i < partes.size(); i++) {
            inicios[i] = inicios[i - 1] + partes.get(i - 1).length();
        }
        List<String> nuevas = new ArrayList<>(partes);
        for (int k = matches.size() - 1; k >= 0; k--) {
            int inicio = matches.get(k)[0];
            int fin = matches.get(k)[1];
            int primero = indiceDe(inicios, partes, inicio);
            int ultimo = indiceDe(inicios, partes, fin - 1);
            String antes = nuevas.get(primero).substring(0, inicio - inicios[primero]);
            String despues = nuevas.get(ultimo).substring(fin - inicios[ultimo]);
            if (primero == ultimo) {
                nuevas.set(primero, antes + reemplazos.get(k) + despues);
            } else {
                nuevas.set(primero, antes + reemplazos.get(k));
                for (int i = primero + 1; i < ultimo; i++) {
                    nuevas.set(i, "");
                }
                nuevas.set(ultimo, despues);
            }
        }
        for (int i = 0; i < textos.size(); i++) {
            if (!nuevas.get(i).equals(partes.get(i))) {
                escribir(textos.get(i), nuevas.get(i));
            }
        }
    }

    /** Indice del {@code w:t} que contiene la posicion (los textos vacios nunca contienen una posicion). */
    private static int indiceDe(int[] inicios, List<String> partes, int posicion) {
        for (int i = partes.size() - 1; i >= 0; i--) {
            if (!partes.get(i).isEmpty() && inicios[i] <= posicion) {
                return i;
            }
        }
        throw new IllegalStateException("Posicion fuera del parrafo: " + posicion);
    }

    /** Texto de un {@code w:t}; por cursor, asi sirve tambien dentro de contenido que XmlBeans no tipa. */
    private static String leer(XmlObject t) {
        XmlCursor c = t.newCursor();
        try {
            return c.getTextValue();
        } finally {
            c.dispose();
        }
    }

    private static void escribir(XmlObject t, String texto) {
        XmlCursor c = t.newCursor();
        try {
            c.setTextValue(texto);
            c.setAttributeText(XML_SPACE, "preserve");
        } finally {
            c.dispose();
        }
    }

    private static String unir(List<XmlObject> textos) {
        StringBuilder sb = new StringBuilder();
        for (XmlObject t : textos) {
            sb.append(leer(t));
        }
        return sb.toString();
    }

    /** Cada parrafo del documento, como la lista ordenada de sus {@code w:t} (sin los de parrafos anidados). */
    private static void recorrerParrafos(XWPFDocument doc, Consumer<List<XmlObject>> accion) {
        List<XmlObject> raices = new ArrayList<>();
        raices.add(doc.getDocument());
        for (XWPFHeader header : doc.getHeaderList()) {
            raices.add(header._getHdrFtr());
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            raices.add(footer._getHdrFtr());
        }
        for (XWPFFootnote nota : doc.getFootnotes()) {
            raices.add(nota.getCTFtnEdn());
        }
        for (XWPFEndnote nota : doc.getEndnotes()) {
            raices.add(nota.getCTFtnEdn());
        }
        for (XmlObject raiz : raices) {
            for (XmlObject parrafo : raiz.selectPath(NS + ".//w:p")) {
                List<XmlObject> textos = new ArrayList<>();
                for (XmlObject run : parrafo.selectPath(NS + ".//w:r")) {
                    if (parrafoMasCercano(run, parrafo)) {
                        textos.addAll(List.of(run.selectPath(NS + "./w:t")));
                    }
                }
                if (!textos.isEmpty()) {
                    accion.accept(textos);
                }
            }
        }
    }

    private static boolean parrafoMasCercano(XmlObject run, XmlObject parrafo) {
        XmlCursor c = run.newCursor();
        XmlCursor p = parrafo.newCursor();
        try {
            while (c.toParent()) {
                if (PARRAFO.equals(c.getName())) {
                    return c.isAtSamePositionAs(p);
                }
            }
            return false;
        } finally {
            c.dispose();
            p.dispose();
        }
    }
}
