package com.rrhh.contratos.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

/**
 * Decoder de prueba: el valor del token elige los claims. Tokens: "admin", "trabajador",
 * "jefatura", "sin-ficha" (Trabajador sin custom:trabajador_id), "admin-suspendida", "admin-plataforma",
 * "admin-plataforma-con-tenant", "operador-grupo", "operador-sin-grupo" y "operador-grupo-sin-claim". Cualquier otro token es invalido.
 */
@TestConfiguration
public class TestJwtConfig {
    public static final String TENANT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    public static final String TRABAJADOR_ID = "11111111-1111-1111-1111-111111111111";
    public static final String JEFATURA_TRABAJADOR_ID = "22222222-2222-2222-2222-222222222222";
    /** custom:user_id del token "operador-grupo"; PlataformaAlcanceIT lo asigna a su grupo. */
    public static final String OPERADOR_GRUPO_ID = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
    /** Subject del token "operador-grupo-sin-claim" (sin custom:user_id): su fila se busca por cognito_sub. */
    public static final String OPERADOR_SIN_CLAIM_SUB = "operador-grupo-sin-claim";
    /** Empresa del token "admin-suspendida"; TenantSuspendidoIT la crea en identity_tenant y la suspende. */
    public static final String TENANT_SUSPENDIBLE = "99999999-9999-9999-9999-999999999999";

    private static final Map<String, Map<String, Object>> CLAIMS = Map.ofEntries(
            Map.entry("admin", Map.of(
                    "email", "admin.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Admin de RRHH",
                    "custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
            Map.entry("trabajador", Map.of(
                    "email", "trabajador.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Trabajador",
                    "custom:user_id", "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "custom:trabajador_id", TRABAJADOR_ID)),
            Map.entry("jefatura", Map.of(
                    "email", "jefatura.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Jefatura",
                    "custom:user_id", "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    "custom:trabajador_id", JEFATURA_TRABAJADOR_ID)),
            Map.entry("sin-ficha", Map.of(
                    "email", "sin.ficha@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Trabajador",
                    "custom:user_id", "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")),
            Map.entry("admin-suspendida", Map.of(
                    "email", "admin.suspendida@rrhh.local",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "Admin de RRHH")),
            Map.entry("admin-plataforma", Map.of(
                    "email", "owner@gmail.com",
                    "custom:role", "AdminPlataforma")),
            Map.entry("admin-plataforma-con-tenant", Map.of(
                    "email", "owner@gmail.com",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "AdminPlataforma")),
            Map.entry("operador-grupo", Map.of(
                    "email", "operador.grupo@rrhh.local",
                    "custom:role", "OperadorSaaS",
                    "custom:user_id", OPERADOR_GRUPO_ID)),
            Map.entry("operador-sin-grupo", Map.of(
                    "email", "operador.solo@rrhh.local",
                    "custom:role", "OperadorSaaS",
                    "custom:user_id", "dddddddd-0000-0000-0000-000000000001")),
            Map.entry("operador-grupo-sin-claim", Map.of(
                    "email", "operador.grupo@rrhh.local",
                    "custom:role", "OperadorSaaS"))
    );

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> {
            Map<String, Object> claims = CLAIMS.get(token);
            if (claims == null) {
                throw new BadJwtException("Token de prueba desconocido: " + token);
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claims(c -> c.putAll(claims))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        };
    }
}
