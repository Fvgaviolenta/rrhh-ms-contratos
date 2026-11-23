package com.rrhh.contratos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.dto.request.EmitirContratoRequest;
import com.rrhh.contratos.dto.response.ContratoResponse;
import com.rrhh.contratos.dto.response.MiContratoResponse;
import com.rrhh.contratos.exception.DomainException;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.ContratoDocumento;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.repository.ContratoDocumentoRepository;
import com.rrhh.contratos.repository.ContratoRepository;
import com.rrhh.contratos.security.TenantContext;
import com.rrhh.contratos.service.PlantillaContratoService.Responsable;
import com.rrhh.contratos.util.PlantillaDocx;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Flujo del contrato desde la plantilla (S3 a S6): RRHH emite un contrato en espera de firma; el trabajador
 * lo completa, lo previsualiza y lo aprueba o rechaza.
 */
@Service
public class FlujoContratoService {
    static final String SIN_FICHA = "Tu usuario no está vinculado a una ficha de trabajador";
    static final String FIRMADO_SIN_DOCUMENTO = "El contrato firmado no tiene documento";
    private static final TypeReference<LinkedHashMap<String, String>> MAPA = new TypeReference<>() {};

    /** PDF listo para enviar, con la fecha de inicio para el nombre del archivo. */
    public record PdfContrato(byte[] pdf, LocalDate fechaInicio) {}

    private final ContratoRepository contratoRepository;
    private final ContratoDocumentoRepository documentoRepository;
    private final PlantillaContratoService plantilla;
    private final AuditoriaContratos auditoria;
    private final TrabajadoresClient trabajadoresClient;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;
    private final ObjectMapper json;

    public FlujoContratoService(
            ContratoRepository contratoRepository,
            ContratoDocumentoRepository documentoRepository,
            PlantillaContratoService plantilla,
            AuditoriaContratos auditoria,
            TrabajadoresClient trabajadoresClient,
            OutboxPublisher outboxPublisher,
            TenantContext tenantContext,
            ObjectMapper json
    ) {
        this.contratoRepository = contratoRepository;
        this.documentoRepository = documentoRepository;
        this.plantilla = plantilla;
        this.auditoria = auditoria;
        this.trabajadoresClient = trabajadoresClient;
        this.outboxPublisher = outboxPublisher;
        this.tenantContext = tenantContext;
        this.json = json;
    }

    /** RRHH emite un contrato PENDIENTE_FIRMA a un trabajador del tenant sin contrato en espera ni firmado. */
    @Transactional
    public ContratoResponse emitir(EmitirContratoRequest request) {
        String tenantId = tenantContext.require().tenantId();
        String trabajadorId = request.trabajadorId();
        trabajadoresClient.exigirDelTenant(trabajadorId);
        contratoRepository.findFirstByTenantIdAndTrabajadorIdAndEstadoIn(tenantId, trabajadorId, ContratoService.ESTADOS_VIGENTES)
                .ifPresent(c -> {
                    throw new DomainException(409, "El trabajador ya tiene un contrato en estado " + c.getEstado());
                });
        Map<String, String> campos = plantilla.validar(Responsable.RRHH, request.campos());

        Instant ahora = Instant.now();
        Contrato contrato = new Contrato();
        contrato.setId(UUID.randomUUID().toString());
        contrato.setTenantId(tenantId);
        contrato.setTrabajadorId(trabajadorId);
        contrato.setSalarioBase(new BigDecimal(campos.get(PlantillaContratoService.SUELDO_BASE)));
        contrato.setJornadaHorasSemanales(Integer.valueOf(campos.get(PlantillaContratoService.HORAS_SEMANALES)));
        contrato.setTipoContrato(campos.get(PlantillaContratoService.TIPO_CONTRATO));
        contrato.setFechaInicio(LocalDate.parse(campos.get(PlantillaContratoService.FECHA_INICIO)));
        String termino = campos.get(PlantillaContratoService.FECHA_TERMINO);
        contrato.setFechaTermino(termino == null ? null : LocalDate.parse(termino));
        contrato.setEstado(EstadoContrato.PENDIENTE_FIRMA);
        contrato.setActivo(false);
        contrato.setCreadoEn(ahora);
        try {
            contratoRepository.saveAndFlush(contrato);
        } catch (DataIntegrityViolationException e) {
            // Otra emision concurrente gano el indice unico de contrato vigente (V5).
            throw new DomainException(409, "El trabajador ya tiene un contrato en espera de firma o firmado");
        }

        ContratoDocumento documento = new ContratoDocumento();
        documento.setId(UUID.randomUUID().toString());
        documento.setTenantId(tenantId);
        documento.setContratoId(contrato.getId());
        documento.setCamposRrhh(aJson(campos));
        documento.setPlantillaSha256(plantilla.sha256());
        documento.setActualizadoEn(ahora);
        documentoRepository.save(documento);

        auditoria.auditar("Contrato", contrato.getId(), "Emision", null, contrato.getSalarioBase().toPlainString());
        outboxPublisher.registrar("contrato.creado", tenantId, contrato.getId(),
                Map.of("trabajadorId", trabajadorId, "contratoId", contrato.getId()));
        return ContratoService.toResponse(contrato);
    }

    /** Estado del contrato del trabajador autenticado; tenant y trabajador salen solo del JWT. */
    @Transactional(readOnly = true)
    public MiContratoResponse mio() {
        TenantContext.AuthenticatedUser actor = trabajadorAutenticado();
        return vigente(actor).map(c -> aMiContrato(c, documentoDe(c).orElse(null)))
                .orElseGet(MiContratoResponse::sinContrato);
    }

    /** Guarda los campos del trabajador y genera el .docx completo; solo con el contrato en espera de firma. */
    @Transactional
    public MiContratoResponse guardarCampos(Map<String, String> campos) {
        TenantContext.AuthenticatedUser actor = trabajadorAutenticado();
        Contrato contrato = exigirEnEspera(actor, "completar los campos de");
        ContratoDocumento documento = documentoDe(contrato)
                .orElseThrow(() -> new DomainException(409, "El contrato en espera no tiene documento; RRHH debe emitirlo de nuevo"));
        Map<String, String> trabajador = plantilla.validar(Responsable.TRABAJADOR, campos);
        byte[] docx;
        try {
            docx = plantilla.generarDocx(deJson(documento.getCamposRrhh()), trabajador);
        } catch (IllegalStateException e) {
            throw new DomainException(500, e.getMessage());
        }
        documento.setCamposTrabajador(aJson(trabajador));
        documento.setDocx(docx);
        documento.setPlantillaSha256(plantilla.sha256());
        documento.setActualizadoEn(Instant.now());
        documentoRepository.save(documento);
        return aMiContrato(contrato, documento);
    }

    /** En espera: previsualizacion convertida en cada llamada. Firmado: el PDF guardado al aprobar. */
    @Transactional(readOnly = true)
    public PdfContrato pdfMio() {
        TenantContext.AuthenticatedUser actor = trabajadorAutenticado();
        Contrato contrato = vigente(actor).orElseThrow(() -> new DomainException(404, "No tienes un contrato"));
        ContratoDocumento documento = documentoDe(contrato).orElse(null);
        if (contrato.getEstado() == EstadoContrato.FIRMADO) {
            if (documento == null || documento.getPdfFirmado() == null) {
                throw new DomainException(404, FIRMADO_SIN_DOCUMENTO);
            }
            return new PdfContrato(documento.getPdfFirmado(), contrato.getFechaInicio());
        }
        if (documento == null || documento.getDocx() == null) {
            throw new DomainException(409, "Aún no hay documento para previsualizar: guarda tus campos primero");
        }
        return new PdfContrato(PlantillaDocx.aPdf(documento.getDocx()), contrato.getFechaInicio());
    }

    /** El trabajador aprueba: el PDF final se convierte una vez y se guarda; el contrato pasa a FIRMADO. */
    @Transactional
    public MiContratoResponse aprobar() {
        TenantContext.AuthenticatedUser actor = trabajadorAutenticado();
        Contrato contrato = exigirEnEspera(actor, "aprobar");
        ContratoDocumento documento = documentoDe(contrato)
                .filter(d -> d.getDocx() != null)
                .orElseThrow(() -> new DomainException(409, "Guarda tus campos para generar el documento antes de aprobar"));
        Instant ahora = Instant.now();
        documento.setPdfFirmado(PlantillaDocx.aPdf(documento.getDocx()));
        documento.setActualizadoEn(ahora);
        documentoRepository.save(documento);
        contrato.setEstado(EstadoContrato.FIRMADO);
        contrato.setActivo(true);
        contrato.setFirmadoEn(ahora);
        contratoRepository.save(contrato);
        auditoria.auditar("Contrato", contrato.getId(), "Firma", EstadoContrato.PENDIENTE_FIRMA.name(), EstadoContrato.FIRMADO.name());
        outboxPublisher.registrar("contrato.firmado", contrato.getTenantId(), contrato.getId(),
                Map.of("trabajadorId", contrato.getTrabajadorId(), "contratoId", contrato.getId()));
        return aMiContrato(contrato, documento);
    }

    /** El trabajador rechaza: el contrato pasa a RECHAZADO y el trabajador vuelve a SIN_CONTRATO. */
    @Transactional
    public MiContratoResponse rechazar(String motivo) {
        TenantContext.AuthenticatedUser actor = trabajadorAutenticado();
        Contrato contrato = exigirEnEspera(actor, "rechazar");
        contrato.setEstado(EstadoContrato.RECHAZADO);
        contrato.setActivo(false);
        contratoRepository.save(contrato);
        String detalle = motivo == null || motivo.isBlank() ? null : motivo.strip();
        auditoria.auditar("Contrato", contrato.getId(), "Rechazo", EstadoContrato.PENDIENTE_FIRMA.name(), detalle);
        outboxPublisher.registrar("contrato.rechazado", contrato.getTenantId(), contrato.getId(),
                Map.of("trabajadorId", contrato.getTrabajadorId(), "contratoId", contrato.getId()));
        return MiContratoResponse.sinContrato();
    }

    /** PDF firmado de cualquier contrato del tenant del admin; 404 si el contrato no es del tenant o no tiene PDF. */
    @Transactional(readOnly = true)
    public PdfContrato pdfAdmin(String contratoId) {
        String tenantId = tenantContext.require().tenantId();
        Contrato contrato = contratoRepository.findByIdAndTenantId(contratoId, tenantId)
                .orElseThrow(() -> new DomainException(404, "Contrato no encontrado"));
        byte[] pdf = documentoDe(contrato).map(ContratoDocumento::getPdfFirmado).orElse(null);
        if (pdf == null) {
            boolean firmado = contrato.getEstado() == EstadoContrato.FIRMADO || contrato.getEstado() == EstadoContrato.FINALIZADO;
            throw new DomainException(404, firmado ? FIRMADO_SIN_DOCUMENTO : "El contrato no tiene un documento firmado");
        }
        return new PdfContrato(pdf, contrato.getFechaInicio());
    }

    private TenantContext.AuthenticatedUser trabajadorAutenticado() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.trabajadorId() == null || actor.trabajadorId().isBlank()) {
            throw new DomainException(403, SIN_FICHA);
        }
        return actor;
    }

    private Optional<Contrato> vigente(TenantContext.AuthenticatedUser actor) {
        return contratoRepository.findFirstByTenantIdAndTrabajadorIdAndEstadoIn(
                actor.tenantId(), actor.trabajadorId(), ContratoService.ESTADOS_VIGENTES);
    }

    private Contrato exigirEnEspera(TenantContext.AuthenticatedUser actor, String accion) {
        Contrato contrato = vigente(actor)
                .orElseThrow(() -> new DomainException(409, "No tienes un contrato en espera de firma"));
        if (contrato.getEstado() != EstadoContrato.PENDIENTE_FIRMA) {
            throw new DomainException(409, "No se puede " + accion + " un contrato en estado " + contrato.getEstado());
        }
        return contrato;
    }

    private Optional<ContratoDocumento> documentoDe(Contrato contrato) {
        return documentoRepository.findByContratoIdAndTenantId(contrato.getId(), contrato.getTenantId());
    }

    private MiContratoResponse aMiContrato(Contrato contrato, ContratoDocumento documento) {
        boolean firmado = contrato.getEstado() == EstadoContrato.FIRMADO;
        boolean pdfFirmado = documento != null && documento.getPdfFirmado() != null;
        boolean listo = firmado ? pdfFirmado : documento != null && documento.getDocx() != null;
        Map<String, String> rrhh = documento == null ? null : deJson(documento.getCamposRrhh());
        Map<String, String> trabajador = documento == null || documento.getCamposTrabajador() == null
                ? null : deJson(documento.getCamposTrabajador());
        return new MiContratoResponse(firmado ? MiContratoResponse.FIRMADO : MiContratoResponse.EN_ESPERA,
                ContratoService.toResponse(contrato, pdfFirmado), rrhh, trabajador, listo);
    }

    private Map<String, String> deJson(String valor) {
        try {
            return json.readValue(valor, MAPA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible leer los campos guardados del contrato", e);
        }
    }

    private String aJson(Map<String, String> valores) {
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar los campos del contrato", e);
        }
    }
}
