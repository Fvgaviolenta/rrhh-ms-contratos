CREATE TABLE contratos_outbox_event (
 id CHAR(36) NOT NULL PRIMARY KEY, tenant_id CHAR(36) NOT NULL, event_type VARCHAR(80) NOT NULL,
 aggregate_id CHAR(36) NOT NULL, payload TEXT NOT NULL, estado VARCHAR(20) NOT NULL, intentos INT NOT NULL DEFAULT 0,
 creado_en DATETIME NOT NULL, publicado_en DATETIME NULL, ultimo_error TEXT NULL
);
CREATE INDEX idx_contratos_outbox_estado ON contratos_outbox_event (estado, creado_en);
