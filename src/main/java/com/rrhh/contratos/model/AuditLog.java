package com.rrhh.contratos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "contratos_audit_log")
public class AuditLog {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;
    @Column(name = "usuario_id", length = 36, columnDefinition = "CHAR(36)")
    private String usuarioId;
    @Column(nullable = false, length = 80)
    private String entidad;
    @Column(name = "entidad_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String entidadId;
    @Column(nullable = false, length = 40)
    private String accion;
    @Column(name = "valores_anteriores", columnDefinition = "TEXT")
    private String valoresAnteriores;
    @Column(name = "valores_nuevos", columnDefinition = "TEXT")
    private String valoresNuevos;
    @Column(name = "fecha_evento", nullable = false)
    private Instant fechaEvento;
}
