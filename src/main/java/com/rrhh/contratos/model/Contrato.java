package com.rrhh.contratos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "contratos_contrato")
public class Contrato {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;
    @Column(name = "trabajador_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String trabajadorId;
    @Column(name = "salario_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal salarioBase;
    @Column(name = "tipo_contrato", nullable = false, length = 40)
    private String tipoContrato;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
    @Column(name = "fecha_termino")
    private LocalDate fechaTermino;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
