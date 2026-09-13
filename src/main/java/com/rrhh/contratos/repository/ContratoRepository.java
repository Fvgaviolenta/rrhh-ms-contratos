package com.rrhh.contratos.repository;

import com.rrhh.contratos.model.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratoRepository extends JpaRepository<Contrato, String> {
    List<Contrato> findByTenantId(String tenantId);
    List<Contrato> findByTenantIdAndTrabajadorId(String tenantId, String trabajadorId);
    Optional<Contrato> findByIdAndTenantId(String id, String tenantId);
    boolean existsByTenantIdAndTrabajadorIdAndActivoTrue(String tenantId, String trabajadorId);
}
