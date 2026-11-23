-- A lo sumo un contrato PENDIENTE_FIRMA o FIRMADO por trabajador y tenant (S2), tambien con solicitudes concurrentes.
ALTER TABLE contratos_contrato ADD COLUMN trabajador_vigente CHAR(36)
    GENERATED ALWAYS AS (CASE WHEN estado IN ('PENDIENTE_FIRMA', 'FIRMADO') THEN trabajador_id ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_contrato_trabajador_vigente ON contratos_contrato (tenant_id, trabajador_vigente);
