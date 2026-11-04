package com.rrhh.contratos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.dto.request.EmitirContratoRequest;
import com.rrhh.contratos.dto.response.ContratoResponse;
import com.rrhh.contratos.exception.DomainException;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.ContratoDocumento;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.repository.ContratoDocumentoRepository;
import com.rrhh.contratos.repository.ContratoRepository;
import com.rrhh.contratos.security.TenantContext;
import com.rrhh.contratos.service.PlantillaContratoService.Responsable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Flujo del contrato desde la plantilla (S3 a S6): RRHH emite un contrato en espera de firma; el trabajador
 * lo completa, lo previsualiza y lo aprueba o rechaza.
 */
@Service
public class FlujoContratoService {
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
        contratoRepository.save(contrato);

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

    private String aJson(Map<String, String> valores) {
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar los campos del contrato", e);
        }
    }
}
