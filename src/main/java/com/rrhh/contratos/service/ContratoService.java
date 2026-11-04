package com.rrhh.contratos.service;

import com.rrhh.contratos.dto.request.ActualizarContratoRequest;
import com.rrhh.contratos.dto.request.CalcularLiquidacionRequest;
import com.rrhh.contratos.dto.request.CrearContratoRequest;
import com.rrhh.contratos.dto.response.ContratoResponse;
import com.rrhh.contratos.dto.response.LiquidacionResponse;
import com.rrhh.contratos.exception.DomainException;
import com.rrhh.contratos.model.Contrato;
import com.rrhh.contratos.model.EstadoContrato;
import com.rrhh.contratos.model.Liquidacion;
import com.rrhh.contratos.repository.ContratoRepository;
import com.rrhh.contratos.repository.LiquidacionRepository;
import com.rrhh.contratos.security.Roles;
import com.rrhh.contratos.security.TenantContext;
import com.rrhh.contratos.util.LiquidacionCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class ContratoService {
    /** Estados que cuentan como "el trabajador tiene contrato": a lo sumo uno por trabajador (S2). */
    public static final EnumSet<EstadoContrato> ESTADOS_VIGENTES = EnumSet.of(EstadoContrato.PENDIENTE_FIRMA, EstadoContrato.FIRMADO);

    private final ContratoRepository contratoRepository;
    private final LiquidacionRepository liquidacionRepository;
    private final AuditoriaContratos auditoria;
    private final TrabajadoresClient trabajadoresClient;
    private final TenantContext tenantContext;
    private final RestTemplate restTemplate;
    private final String asistenciaBaseUrl;
    private final OutboxPublisher outboxPublisher;

    public ContratoService(
            ContratoRepository contratoRepository,
            LiquidacionRepository liquidacionRepository,
            AuditoriaContratos auditoria,
            TrabajadoresClient trabajadoresClient,
            TenantContext tenantContext,
            @Value("${rrhh.integrations.asistencia.base-url:http://localhost:8084}") String asistenciaBaseUrl,
            OutboxPublisher outboxPublisher
    ) {
        this.contratoRepository = contratoRepository;
        this.liquidacionRepository = liquidacionRepository;
        this.auditoria = auditoria;
        this.trabajadoresClient = trabajadoresClient;
        this.tenantContext = tenantContext;
        this.restTemplate = new RestTemplate();
        this.asistenciaBaseUrl = asistenciaBaseUrl.replaceAll("/$", "");
        this.outboxPublisher = outboxPublisher;
    }

    public List<ContratoResponse> listar() {
        return contratoRepository.findByTenantId(tenantContext.require().tenantId()).stream().map(ContratoService::toResponse).toList();
    }

    public List<ContratoResponse> listarPorTenantPlataforma(String tenantId) {
        exigirOperadorSaas();
        return contratoRepository.findByTenantId(tenantId).stream().map(ContratoService::toResponse).toList();
    }

    public List<ContratoResponse> porTrabajador(String trabajadorId) {
        validarAccesoTrabajador(trabajadorId);
        return contratoRepository.findByTenantIdAndTrabajadorId(tenantContext.require().tenantId(), trabajadorId)
                .stream().map(ContratoService::toResponse).toList();
    }

    public ContratoResponse obtener(String contratoId) {
        Contrato contrato = buscar(contratoId);
        validarAccesoTrabajador(contrato.getTrabajadorId());
        return toResponse(contrato);
    }

    @Transactional
    public ContratoResponse crear(CrearContratoRequest request) {
        String tenantId = tenantContext.require().tenantId();
        trabajadoresClient.exigirDelTenant(request.trabajadorId());
        if (contratoRepository.existsByTenantIdAndTrabajadorIdAndEstadoIn(tenantId, request.trabajadorId(), ESTADOS_VIGENTES)) {
            throw new DomainException(400, "El trabajador ya tiene un contrato activo", "trabajador_id");
        }
        Contrato contrato = new Contrato();
        contrato.setId(UUID.randomUUID().toString());
        contrato.setTenantId(tenantId);
        contrato.setTrabajadorId(request.trabajadorId());
        contrato.setSalarioBase(request.salarioBase());
        contrato.setTipoContrato(request.tipoContrato());
        contrato.setFechaInicio(request.fechaInicio());
        contrato.setEstado(EstadoContrato.FIRMADO);
        contrato.setActivo(true);
        contrato.setCreadoEn(Instant.now());
        contratoRepository.save(contrato);
        auditoria.auditar("Contrato", contrato.getId(), "Creacion", null, contrato.getSalarioBase().toPlainString());
        outboxPublisher.registrar("contrato.creado", contrato.getTenantId(), contrato.getId(),
                java.util.Map.of("trabajadorId", contrato.getTrabajadorId(), "contratoId", contrato.getId()));
        return toResponse(contrato);
    }

    @Transactional
    public ContratoResponse actualizar(String contratoId, ActualizarContratoRequest request) {
        Contrato contrato = buscar(contratoId);
        exigirNoPendienteNiRechazado(contrato, "modificar");
        BigDecimal anterior = contrato.getSalarioBase();
        if (request.salarioBase() != null) {
            contrato.setSalarioBase(request.salarioBase());
            auditoria.auditar("Contrato", contrato.getId(), "Modificacion", anterior.toPlainString(), request.salarioBase().toPlainString());
        }
        if (request.tipoContrato() != null) {
            contrato.setTipoContrato(request.tipoContrato());
        }
        return toResponse(contratoRepository.save(contrato));
    }

    @Transactional
    public ContratoResponse finalizar(String contratoId) {
        Contrato contrato = buscar(contratoId);
        if (contrato.getEstado() != EstadoContrato.FIRMADO) {
            throw new DomainException(409, "No se puede finalizar un contrato en estado " + contrato.getEstado());
        }
        contrato.setEstado(EstadoContrato.FINALIZADO);
        contrato.setActivo(false);
        contrato.setFechaTermino(LocalDate.now());
        contratoRepository.save(contrato);
        outboxPublisher.registrar("contrato.finalizado", contrato.getTenantId(), contrato.getId(),
                java.util.Map.of("trabajadorId", contrato.getTrabajadorId(), "contratoId", contrato.getId()));
        return toResponse(contrato);
    }

    @Transactional
    public LiquidacionResponse calcularLiquidacion(CalcularLiquidacionRequest request) {
        Contrato contrato = buscar(request.contratoId());
        validarAccesoTrabajador(contrato.getTrabajadorId());
        exigirNoPendienteNiRechazado(contrato, "calcular la liquidación de");
        BigDecimal montoHorasExtra = obtenerHorasExtraAprobadas(contrato.getTrabajadorId(), request.periodo());
        BigDecimal totalHaberes = contrato.getSalarioBase().add(montoHorasExtra);
        LiquidacionCalculator.Resultado r = LiquidacionCalculator.calcular(totalHaberes, contrato.getTipoContrato());
        Liquidacion liquidacion = new Liquidacion();
        liquidacion.setId(UUID.randomUUID().toString());
        liquidacion.setTenantId(contrato.getTenantId());
        liquidacion.setContratoId(contrato.getId());
        liquidacion.setPeriodo(request.periodo());
        liquidacion.setSueldoBase(contrato.getSalarioBase());
        liquidacion.setMontoHorasExtra(montoHorasExtra);
        liquidacion.setTotalHaberes(totalHaberes);
        liquidacion.setAfpDescuento(r.afpDescuento());
        liquidacion.setSaludDescuento(r.saludDescuento());
        liquidacion.setSeguroCesantia(r.seguroCesantia());
        liquidacion.setImpuestoSegundaCategoria(r.impuestoSegundaCategoria());
        liquidacion.setSueldoLiquido(r.sueldoLiquido());
        liquidacion.setEstado("CALCULADA");
        liquidacion.setCreadoEn(Instant.now());
        liquidacionRepository.save(liquidacion);
        return toResponse(liquidacion);
    }

    private Contrato buscar(String contratoId) {
        return contratoRepository.findByIdAndTenantId(contratoId, tenantContext.require().tenantId())
                .orElseThrow(() -> new DomainException(404, "Contrato no encontrado"));
    }

    private static void exigirNoPendienteNiRechazado(Contrato contrato, String accion) {
        EstadoContrato estado = contrato.getEstado();
        if (estado == EstadoContrato.PENDIENTE_FIRMA || estado == EstadoContrato.RECHAZADO) {
            throw new DomainException(409, "No se puede " + accion + " un contrato en estado " + estado);
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal obtenerHorasExtraAprobadas(String trabajadorId, String periodo) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) throw new DomainException(401, "No fue posible autorizar la consulta de horas extra");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
        try {
            var response = restTemplate.exchange(asistenciaBaseUrl + "/api/v1/horas-extra/resumen?trabajador_id=" + trabajadorId + "&periodo=" + periodo,
                    HttpMethod.GET, new HttpEntity<>(headers), java.util.Map.class);
            Object datos = response.getBody() == null ? null : response.getBody().get("datos");
            if (!(datos instanceof java.util.Map<?, ?> resumen) || resumen.get("monto_total") == null) return BigDecimal.ZERO;
            return new BigDecimal(resumen.get("monto_total").toString());
        } catch (RestClientException ex) {
            throw new DomainException(503, "No fue posible obtener las horas extra aprobadas");
        }
    }

    private void exigirOperadorSaas() {
        if (!Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(tenantContext.current().role()))) {
            throw new DomainException(403, "Acceso exclusivo para el operador SaaS");
        }
    }

    private void validarAccesoTrabajador(String trabajadorId) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String authority = Roles.authorityFromClaim(actor.role());
        if (Roles.TRABAJADOR.equals(authority) && (actor.trabajadorId() == null || !actor.trabajadorId().equals(trabajadorId))) {
            throw new DomainException(403, "No puede consultar salarios de otro trabajador");
        }
    }

    static ContratoResponse toResponse(Contrato c) {
        return new ContratoResponse(c.getId(), c.getTenantId(), c.getTrabajadorId(), c.getSalarioBase(),
                c.getTipoContrato(), c.getFechaInicio(), c.getFechaTermino(), c.isActivo(),
                c.getEstado(), c.getJornadaHorasSemanales());
    }

    private LiquidacionResponse toResponse(Liquidacion l) {
        return new LiquidacionResponse(l.getId(), l.getContratoId(), l.getPeriodo(), l.getSueldoBase(), l.getMontoHorasExtra(), l.getTotalHaberes(),
                l.getAfpDescuento(), l.getSaludDescuento(), l.getSeguroCesantia(),
                l.getImpuestoSegundaCategoria(), l.getSueldoLiquido(), l.getEstado());
    }
}
