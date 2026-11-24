-- Tablas de rrhh-identity que lee este servicio (ADR-020): estado de empresa para el filtro de suspensión y
-- grupos de empresas para el alcance de plataforma. Solo tests; en la base compartida las crea identity.
CREATE TABLE IF NOT EXISTS identity_tenant (
    id CHAR(36) NOT NULL PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    estado VARCHAR(30) NOT NULL
);
CREATE TABLE IF NOT EXISTS identity_grupo_tenant (
    grupo_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    PRIMARY KEY (grupo_id, tenant_id)
);
CREATE TABLE IF NOT EXISTS identity_grupo_operador (
    grupo_id CHAR(36) NOT NULL,
    usuario_id CHAR(36) NOT NULL,
    PRIMARY KEY (grupo_id, usuario_id)
);
