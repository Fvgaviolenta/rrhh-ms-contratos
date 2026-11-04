package com.rrhh.contratos.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Documento del contrato generado desde la plantilla: valores por responsable, .docx y PDF firmado (S6). */
@Getter
@Setter
@Entity
@Table(name = "contratos_documento")
public class ContratoDocumento {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;
    @Column(name = "contrato_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)")
    private String contratoId;
    @Column(name = "campos_rrhh", nullable = false, columnDefinition = "TEXT")
    private String camposRrhh;
    @Column(name = "campos_trabajador", columnDefinition = "TEXT")
    private String camposTrabajador;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "docx", columnDefinition = "LONGBLOB")
    private byte[] docx;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "pdf_firmado", columnDefinition = "LONGBLOB")
    private byte[] pdfFirmado;
    @Column(name = "plantilla_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String plantillaSha256;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
