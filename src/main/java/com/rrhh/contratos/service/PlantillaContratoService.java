package com.rrhh.contratos.service;

import com.rrhh.contratos.dto.ApiResponse;
import com.rrhh.contratos.dto.response.CampoPlantillaResponse;
import com.rrhh.contratos.exception.ValidacionCamposException;
import com.rrhh.contratos.util.PlantillaDocx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Plantilla .docx global del contrato (S1). Se carga al arrancar: el prefijo de cada campo dice quien lo
 * llena ({@code rrhh_} o {@code trabajador_}); cinco claves {@code rrhh_} tienen tipo fijo y las cuatro
 * primeras son obligatorias en la plantilla. Un prefijo desconocido o una clave tipada ausente hace fallar
 * el arranque con un mensaje que nombra el campo.
 */
@Service
public class PlantillaContratoService {
    public enum Responsable { RRHH, TRABAJADOR }

    public enum TipoCampo { TEXTO, NUMERO, ENTERO, FECHA, OPCION }

    public static final String PREFIJO_RRHH = "rrhh_";
    public static final String PREFIJO_TRABAJADOR = "trabajador_";
    public static final String SUELDO_BASE = "rrhh_sueldo_base";
    public static final String HORAS_SEMANALES = "rrhh_horas_semanales";
    public static final String TIPO_CONTRATO = "rrhh_tipo_contrato";
    public static final String FECHA_INICIO = "rrhh_fecha_inicio";
    public static final String FECHA_TERMINO = "rrhh_fecha_termino";
    public static final List<String> TIPOS_CONTRATO = List.of("INDEFINIDO", "PLAZO_FIJO", "HONORARIOS");
    public static final String SIN_FECHA_TERMINO = "Indefinido";
    static final int HORAS_MAXIMAS = 42;
    static final int LARGO_MAXIMO_TEXTO = 500;

    private static final List<String> CLAVES_OBLIGATORIAS = List.of(SUELDO_BASE, HORAS_SEMANALES, TIPO_CONTRATO, FECHA_INICIO);
    private static final Pattern SUELDO = Pattern.compile("^[0-9]+(\\.[0-9]{1,2})?$");
    private static final Pattern ENTERO = Pattern.compile("^[0-9]{1,9}$");

    private final byte[] plantilla;
    private final String sha256;
    private final List<CampoPlantillaResponse> campos;
    private final Map<String, CampoPlantillaResponse> porClave = new LinkedHashMap<>();

    @Autowired
    public PlantillaContratoService(
            @Value("${rrhh.contratos.plantilla:classpath:plantillas/contrato-estandar.docx}") Resource recurso) {
        this(leer(recurso));
    }

    public PlantillaContratoService(byte[] plantilla) {
        this.plantilla = plantilla.clone();
        List<String> claves = PlantillaDocx.extraerClaves(this.plantilla);
        List<String> invalidas = claves.stream()
                .filter(c -> !c.startsWith(PREFIJO_RRHH) && !c.startsWith(PREFIJO_TRABAJADOR))
                .toList();
        if (!invalidas.isEmpty()) {
            throw new IllegalStateException("La plantilla de contrato tiene campos sin prefijo rrhh_ ni trabajador_: "
                    + String.join(", ", invalidas));
        }
        List<String> faltantes = CLAVES_OBLIGATORIAS.stream().filter(c -> !claves.contains(c)).toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("La plantilla de contrato no tiene los campos obligatorios: "
                    + String.join(", ", faltantes));
        }
        List<CampoPlantillaResponse> lista = new ArrayList<>();
        for (String clave : claves) {
            CampoPlantillaResponse campo = describir(clave);
            lista.add(campo);
            porClave.put(clave, campo);
        }
        this.campos = List.copyOf(lista);
        this.sha256 = calcularSha256(this.plantilla);
    }

    public List<CampoPlantillaResponse> campos() { return campos; }

    public String sha256() { return sha256; }

    public byte[] bytes() { return plantilla.clone(); }

    /**
     * Valida los valores que envia un responsable y devuelve los normalizados (sin espacios al borde, en orden
     * de la plantilla). {@code rrhh_fecha_termino} vacio se omite. Todas las violaciones van juntas en una
     * {@link ValidacionCamposException}.
     */
    public Map<String, String> validar(Responsable responsable, Map<String, String> valores) {
        List<ApiResponse.ErrorItem> errores = new ArrayList<>();
        for (String clave : valores.keySet()) {
            CampoPlantillaResponse campo = porClave.get(clave);
            if (campo == null) {
                errores.add(new ApiResponse.ErrorItem(clave, "El campo no existe en la plantilla"));
            } else if (campo.responsable() != responsable) {
                errores.add(new ApiResponse.ErrorItem(clave, "Este campo no lo completa " + nombre(responsable)));
            }
        }
        Map<String, String> normalizados = new LinkedHashMap<>();
        for (CampoPlantillaResponse campo : campos) {
            if (campo.responsable() != responsable) {
                continue;
            }
            String valor = valores.get(campo.clave());
            valor = valor == null ? "" : valor.strip();
            if (valor.isEmpty()) {
                if (campo.obligatorio()) {
                    errores.add(new ApiResponse.ErrorItem(campo.clave(), "El campo es obligatorio"));
                }
                continue;
            }
            String error = validarValor(campo, valor);
            if (error != null) {
                errores.add(new ApiResponse.ErrorItem(campo.clave(), error));
            } else {
                normalizados.put(campo.clave(), valor);
            }
        }
        if (responsable == Responsable.RRHH) {
            validarFechaTermino(normalizados, errores);
        }
        if (!errores.isEmpty()) {
            throw new ValidacionCamposException(errores);
        }
        return normalizados;
    }

    /** Rellena la plantilla con los valores ya validados de ambos lados; sin fecha de termino escribe "Indefinido". */
    public byte[] generarDocx(Map<String, String> rrhh, Map<String, String> trabajador) {
        Map<String, String> valores = new LinkedHashMap<>(rrhh);
        valores.putAll(trabajador);
        if (porClave.containsKey(FECHA_TERMINO)) {
            valores.putIfAbsent(FECHA_TERMINO, SIN_FECHA_TERMINO);
        }
        return PlantillaDocx.rellenar(plantilla, valores);
    }

    private void validarFechaTermino(Map<String, String> normalizados, List<ApiResponse.ErrorItem> errores) {
        if (!porClave.containsKey(FECHA_TERMINO) || tieneError(errores, FECHA_TERMINO)) {
            return;
        }
        String termino = normalizados.get(FECHA_TERMINO);
        if (termino == null) {
            if ("PLAZO_FIJO".equals(normalizados.get(TIPO_CONTRATO))) {
                errores.add(new ApiResponse.ErrorItem(FECHA_TERMINO, "La fecha de término es obligatoria en un contrato a plazo fijo"));
            }
            return;
        }
        String inicio = normalizados.get(FECHA_INICIO);
        if (inicio != null && !LocalDate.parse(termino).isAfter(LocalDate.parse(inicio))) {
            errores.add(new ApiResponse.ErrorItem(FECHA_TERMINO, "La fecha de término debe ser posterior a la fecha de inicio"));
        }
    }

    private static boolean tieneError(List<ApiResponse.ErrorItem> errores, String clave) {
        return errores.stream().anyMatch(e -> clave.equals(e.campo()));
    }

    private static String validarValor(CampoPlantillaResponse campo, String valor) {
        switch (campo.tipo()) {
            case NUMERO -> {
                if (!SUELDO.matcher(valor).matches() || new BigDecimal(valor).signum() <= 0) {
                    return "Debe ser un número mayor que 0, sin separador de miles (ejemplo: 900000)";
                }
            }
            case ENTERO -> {
                if (!ENTERO.matcher(valor).matches()) {
                    return "Debe ser un número entero entre 1 y " + HORAS_MAXIMAS;
                }
                int n = Integer.parseInt(valor);
                if (n < 1 || n > HORAS_MAXIMAS) {
                    return "Debe ser un número entero entre 1 y " + HORAS_MAXIMAS;
                }
            }
            case OPCION -> {
                if (!campo.opciones().contains(valor)) {
                    return "Debe ser uno de: " + String.join(", ", campo.opciones());
                }
            }
            case FECHA -> {
                try {
                    LocalDate.parse(valor);
                } catch (DateTimeParseException e) {
                    return "Debe ser una fecha con formato AAAA-MM-DD";
                }
            }
            case TEXTO -> {
                if (valor.length() > LARGO_MAXIMO_TEXTO) {
                    return "Debe tener entre 1 y " + LARGO_MAXIMO_TEXTO + " caracteres";
                }
            }
        }
        return null;
    }

    private static CampoPlantillaResponse describir(String clave) {
        Responsable responsable = clave.startsWith(PREFIJO_RRHH) ? Responsable.RRHH : Responsable.TRABAJADOR;
        String sinPrefijo = clave.substring(responsable == Responsable.RRHH ? PREFIJO_RRHH.length() : PREFIJO_TRABAJADOR.length());
        String etiqueta = sinPrefijo.replace('_', ' ').strip();
        if (!etiqueta.isEmpty()) {
            etiqueta = Character.toUpperCase(etiqueta.charAt(0)) + etiqueta.substring(1);
        }
        TipoCampo tipo = switch (clave) {
            case SUELDO_BASE -> TipoCampo.NUMERO;
            case HORAS_SEMANALES -> TipoCampo.ENTERO;
            case TIPO_CONTRATO -> TipoCampo.OPCION;
            case FECHA_INICIO, FECHA_TERMINO -> TipoCampo.FECHA;
            default -> TipoCampo.TEXTO;
        };
        List<String> opciones = tipo == TipoCampo.OPCION ? TIPOS_CONTRATO : List.of();
        return new CampoPlantillaResponse(clave, etiqueta, responsable, tipo, opciones, !FECHA_TERMINO.equals(clave));
    }

    private static String nombre(Responsable responsable) {
        return responsable == Responsable.RRHH ? "RRHH" : "el trabajador";
    }

    private static byte[] leer(Resource recurso) {
        try (InputStream in = recurso.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible leer la plantilla de contrato " + recurso.getDescription(), e);
        }
    }

    private static String calcularSha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
