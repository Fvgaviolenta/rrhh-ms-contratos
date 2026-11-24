package com.rrhh.contratos.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantEstadoCacheTest {

    private static final String SQL = "SELECT estado FROM identity_tenant WHERE id = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
    private final TenantEstadoCache cache = new TenantEstadoCache(jdbc, clock);

    @SuppressWarnings("unchecked")
    private void enLaBase(List<String> primera, List<String> segunda) {
        when(jdbc.queryForList(eq(SQL), eq(String.class), anyString())).thenReturn(primera, segunda);
    }

    @Test
    void dentroDe60SegundosUsaElEstadoGuardadoYDespuesReleeLaBase() {
        enLaBase(List.of("ACTIVO"), List.of("SUSPENDIDO"));
        assertEquals("ACTIVO", cache.estado("t"));
        clock.avanzar(59);
        assertEquals("ACTIVO", cache.estado("t"));
        clock.avanzar(1);
        assertEquals("SUSPENDIDO", cache.estado("t"));
        verify(jdbc, times(2)).queryForList(eq(SQL), eq(String.class), any(Object[].class));
    }

    @Test
    void invalidarObligaALeerLaBase() {
        enLaBase(List.of("ACTIVO"), List.of("SUSPENDIDO"));
        cache.estado("t");
        cache.invalidar("t");
        assertEquals("SUSPENDIDO", cache.estado("t"));
    }

    @Test
    void sinFilaDevuelveNull() {
        enLaBase(List.of(), List.of());
        assertNull(cache.estado("t"));
    }

    private static final class MutableClock extends Clock {
        private Instant ahora;

        MutableClock(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
