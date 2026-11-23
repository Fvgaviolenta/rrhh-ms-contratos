ALTER TABLE contratos_contrato ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'FIRMADO';

UPDATE contratos_contrato SET estado = CASE WHEN activo THEN 'FIRMADO' ELSE 'FINALIZADO' END;

ALTER TABLE contratos_contrato ADD COLUMN jornada_horas_semanales INT NULL;

ALTER TABLE contratos_contrato ADD COLUMN firmado_en DATETIME NULL;

CREATE INDEX idx_contrato_tenant_trab_estado ON contratos_contrato (tenant_id, trabajador_id, estado);

CREATE TABLE contratos_documento (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    contrato_id CHAR(36) NOT NULL,
    campos_rrhh TEXT NOT NULL,
    campos_trabajador TEXT NULL,
    docx LONGBLOB NULL,
    pdf_firmado LONGBLOB NULL,
    plantilla_sha256 CHAR(64) NOT NULL,
    actualizado_en DATETIME NOT NULL,
    CONSTRAINT uk_documento_contrato UNIQUE (contrato_id),
    CONSTRAINT fk_documento_contrato FOREIGN KEY (contrato_id) REFERENCES contratos_contrato (id)
);
