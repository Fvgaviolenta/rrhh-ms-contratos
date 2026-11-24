package com.rrhh.contratos.service;

import com.rrhh.contratos.exception.DomainException;
import com.rrhh.contratos.security.Roles;
import com.rrhh.contratos.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Alcance de los roles de plataforma (S3, ADR-020), leído de las tablas de rrhh-identity en la base compartida:
 * el AdminPlataforma alcanza toda empresa cliente (nunca la de slug {@code plataforma}); un OperadorSaaS solo
 * las empresas de sus grupos. Fuera de alcance, 404, igual que el aislamiento entre empresas.
 */
@Service
public class AlcancePlataforma {

    private final JdbcTemplate jdbc;

    public AlcancePlataforma(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void exigir(TenantContext.AuthenticatedUser actor, String tenantId) {
        if (!alcanza(actor, tenantId)) {
            throw new DomainException(404, "Empresa no encontrada");
        }
    }

    private boolean alcanza(TenantContext.AuthenticatedUser actor, String tenantId) {
        if (actor == null || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        String autoridad = Roles.authorityFromClaim(actor.role());
        if (Roles.ADMIN_PLATAFORMA.equals(autoridad)) {
            Integer empresas = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM identity_tenant WHERE id = ? AND slug <> 'plataforma'", Integer.class, tenantId);
            return empresas != null && empresas > 0;
        }
        if (!Roles.OPERADOR_SAAS.equals(autoridad)) {
            return false;
        }
        String operadorId = operadorId(actor);
        if (operadorId == null) {
            return false;
        }
        Integer asignaciones = jdbc.queryForObject("""
                SELECT COUNT(*) FROM identity_grupo_tenant gt
                JOIN identity_grupo_operador go ON go.grupo_id = gt.grupo_id
                WHERE go.usuario_id = ? AND gt.tenant_id = ?""", Integer.class, operadorId, tenantId);
        return asignaciones != null && asignaciones > 0;
    }

    /** {@code custom:user_id} del token y, si falta, la fila OperadorSaaS de su {@code cognito_sub} (plan 1). */
    private String operadorId(TenantContext.AuthenticatedUser actor) {
        if (actor.userId() != null && !actor.userId().isBlank()) {
            return actor.userId();
        }
        if (actor.cognitoSub() == null || actor.cognitoSub().isBlank()) {
            return null;
        }
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM identity_usuario WHERE cognito_sub = ? AND rol = 'OperadorSaaS'", String.class, actor.cognitoSub());
        return ids.isEmpty() ? null : ids.get(0);
    }
}
