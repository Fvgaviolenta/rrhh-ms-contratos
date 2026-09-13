CREATE TABLE contratos_contrato (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    trabajador_id CHAR(36) NOT NULL,
    salario_base DECIMAL(14,2) NOT NULL,
    tipo_contrato VARCHAR(40) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_termino DATE NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en DATETIME NOT NULL
);

CREATE TABLE contratos_liquidacion (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    contrato_id CHAR(36) NOT NULL,
    periodo VARCHAR(20) NOT NULL,
    sueldo_base DECIMAL(14,2) NOT NULL,
    afp_descuento DECIMAL(14,2) NOT NULL,
    salud_descuento DECIMAL(14,2) NOT NULL,
    seguro_cesantia DECIMAL(14,2) NOT NULL,
    impuesto_segunda_categoria DECIMAL(14,2) NOT NULL,
    sueldo_liquido DECIMAL(14,2) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_liquidacion_contrato FOREIGN KEY (contrato_id) REFERENCES contratos_contrato (id)
);

CREATE TABLE contratos_audit_log (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    usuario_id CHAR(36) NULL,
    entidad VARCHAR(80) NOT NULL,
    entidad_id CHAR(36) NOT NULL,
    accion VARCHAR(40) NOT NULL,
    valores_anteriores TEXT NULL,
    valores_nuevos TEXT NULL,
    fecha_evento DATETIME NOT NULL
);

CREATE INDEX idx_contrato_tenant_trab ON contratos_contrato (tenant_id, trabajador_id, activo);
