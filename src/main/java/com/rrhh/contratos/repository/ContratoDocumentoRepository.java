package com.rrhh.contratos.repository;

import com.rrhh.contratos.model.ContratoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContratoDocumentoRepository extends JpaRepository<ContratoDocumento, String> {
    Optional<ContratoDocumento> findByContratoIdAndTenantId(String contratoId, String tenantId);
}
