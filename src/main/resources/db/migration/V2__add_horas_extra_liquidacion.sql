ALTER TABLE contratos_liquidacion
    ADD COLUMN monto_horas_extra DECIMAL(14,2) NOT NULL DEFAULT 0 AFTER sueldo_base,
    ADD COLUMN total_haberes DECIMAL(14,2) NOT NULL DEFAULT 0 AFTER monto_horas_extra;
