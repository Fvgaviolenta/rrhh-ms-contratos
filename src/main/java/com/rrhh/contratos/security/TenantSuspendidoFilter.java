package com.rrhh.contratos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Corta con 403 todo request cuyo token traiga el {@code tenant_id} de una empresa SUSPENDIDO (S6), también con
 * un JWT emitido antes de suspender. Los roles de plataforma nunca se bloquean aquí.
 */
@Component
public class TenantSuspendidoFilter extends OncePerRequestFilter {

    public static final String MENSAJE = "La empresa está suspendida";
    private static final String SUSPENDIDO = "SUSPENDIDO";

    private final TenantContext tenantContext;
    private final TenantEstadoCache tenantEstadoCache;
    private final ObjectMapper objectMapper;

    public TenantSuspendidoFilter(TenantContext tenantContext, TenantEstadoCache tenantEstadoCache, ObjectMapper objectMapper) {
        this.tenantContext = tenantContext;
        this.tenantEstadoCache = tenantEstadoCache;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantContext.AuthenticatedUser actor = tenantContext.find().orElse(null);
        if (actor != null && bloqueado(actor)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(403, MENSAJE, List.of()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean bloqueado(TenantContext.AuthenticatedUser actor) {
        String autoridad = Roles.authorityFromClaim(actor.role());
        if (Roles.ADMIN_PLATAFORMA.equals(autoridad) || Roles.OPERADOR_SAAS.equals(autoridad)) {
            return false;
        }
        String tenantId = actor.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return SUSPENDIDO.equals(tenantEstadoCache.estado(tenantId));
    }
}
