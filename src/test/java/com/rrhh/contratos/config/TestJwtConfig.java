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
 * "jefatura" y "sin-ficha" (Trabajador sin custom:trabajador_id). Cualquier otro token es invalido.
 */
@TestConfiguration
public class TestJwtConfig {
    public static final String TENANT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    public static final String TRABAJADOR_ID = "11111111-1111-1111-1111-111111111111";
    public static final String JEFATURA_TRABAJADOR_ID = "22222222-2222-2222-2222-222222222222";

    private static final Map<String, Map<String, Object>> CLAIMS = Map.of(
            "admin", Map.of(
                    "email", "admin.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Admin de RRHH",
                    "custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            "trabajador", Map.of(
                    "email", "trabajador.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Trabajador",
                    "custom:user_id", "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    "custom:trabajador_id", TRABAJADOR_ID),
            "jefatura", Map.of(
                    "email", "jefatura.demo@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Jefatura",
                    "custom:user_id", "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    "custom:trabajador_id", JEFATURA_TRABAJADOR_ID),
            "sin-ficha", Map.of(
                    "email", "sin.ficha@rrhh.local",
                    "custom:tenant_id", TENANT_ID,
                    "custom:role", "Trabajador",
                    "custom:user_id", "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
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
