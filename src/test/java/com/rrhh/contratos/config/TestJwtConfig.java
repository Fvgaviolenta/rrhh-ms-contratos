package com.rrhh.contratos.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

@TestConfiguration
public class TestJwtConfig {
    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("admin")
                .claims(c -> c.putAll(Map.of(
                        "email", "admin.demo@rrhh.local",
                        "custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "custom:role", "Admin de RRHH",
                        "custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
                )))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
