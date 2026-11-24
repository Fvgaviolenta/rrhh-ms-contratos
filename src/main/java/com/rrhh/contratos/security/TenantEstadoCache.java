package com.rrhh.contratos.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de cada empresa leído de {@code identity_tenant} (tabla de rrhh-identity en la base compartida), con
 * expiración de 60 s (S6, ADR-020): una suspensión se ve aquí, como mucho, un minuto después.
 */
@Component
public class TenantEstadoCache {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final String SQL = "SELECT estado FROM identity_tenant WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();

    @Autowired
    public TenantEstadoCache(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    TenantEstadoCache(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Estado guardado de la empresa, o null si no tiene fila en identity_tenant. */
    public String estado(String tenantId) {
        Instant ahora = clock.instant();
        Entrada entrada = entradas.get(tenantId);
        if (entrada != null && ahora.isBefore(entrada.expira())) {
            return entrada.estado();
        }
        List<String> filas = jdbc.queryForList(SQL, String.class, tenantId);
        String estado = filas.isEmpty() ? null : filas.get(0);
        entradas.put(tenantId, new Entrada(estado, ahora.plus(TTL)));
        return estado;
    }

    /** Olvida el estado de una empresa: la siguiente consulta lo lee de la base. */
    public void invalidar(String tenantId) {
        entradas.remove(tenantId);
    }

    private record Entrada(String estado, Instant expira) {
    }
}
