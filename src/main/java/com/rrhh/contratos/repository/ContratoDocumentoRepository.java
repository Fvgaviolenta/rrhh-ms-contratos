package com.rrhh.contratos.repository;

import com.rrhh.contratos.model.ContratoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface ContratoDocumentoRepository extends JpaRepository<ContratoDocumento, String> {
    Optional<ContratoDocumento> findByContratoIdAndTenantId(String contratoId, String tenantId);

    /** Ids de los contratos del tenant con PDF firmado, en una sola consulta y sin leer los BLOB. */
    @Query("select d.contratoId from ContratoDocumento d where d.tenantId = :tenantId and d.pdfFirmado is not null")
    Set<String> contratoIdsConPdfFirmado(@Param("tenantId") String tenantId);
}
