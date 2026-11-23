package com.rrhh.contratos.service;

import com.rrhh.contratos.model.AuditLog;
import com.rrhh.contratos.repository.AuditLogRepository;
import com.rrhh.contratos.security.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Escribe una fila de {@code audit_log} con el tenant y el usuario del JWT actual. */
@Component
public class AuditoriaContratos {
    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;

    public AuditoriaContratos(AuditLogRepository auditLogRepository, TenantContext tenantContext) {
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
    }

    public void auditar(String entidad, String entidadId, String accion, String anterior, String nuevo) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID().toString());
        log.setTenantId(actor.tenantId());
        log.setUsuarioId(actor.userId());
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setAccion(accion);
        log.setValoresAnteriores(anterior);
        log.setValoresNuevos(nuevo);
        log.setFechaEvento(Instant.now());
        auditLogRepository.save(log);
    }
}
