package com.rrhh.contratos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "contratos_liquidacion")
public class Liquidacion {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;
    @Column(name = "contrato_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String contratoId;
    @Column(nullable = false, length = 20)
    private String periodo;
    @Column(name = "sueldo_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal sueldoBase;
    @Column(name = "afp_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal afpDescuento;
    @Column(name = "salud_descuento", nullable = false, precision = 14, scale = 2)
    private BigDecimal saludDescuento;
    @Column(name = "seguro_cesantia", nullable = false, precision = 14, scale = 2)
    private BigDecimal seguroCesantia;
    @Column(name = "impuesto_segunda_categoria", nullable = false, precision = 14, scale = 2)
    private BigDecimal impuestoSegundaCategoria;
    @Column(name = "sueldo_liquido", nullable = false, precision = 14, scale = 2)
    private BigDecimal sueldoLiquido;
    @Column(nullable = false, length = 30)
    private String estado;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
