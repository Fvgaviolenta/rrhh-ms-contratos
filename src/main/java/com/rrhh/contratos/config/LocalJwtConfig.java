package com.rrhh.contratos.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Configuration
@Profile("local")
public class LocalJwtConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                String[] parts = token.split("\\.");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("JWT local inválido");
                }
                String json = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
                Map<String, Object> claims = objectMapper.readValue(json, new TypeReference<>() {});
                Instant iat = claims.get("iat") instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : Instant.now();
                Instant exp = claims.get("exp") instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : Instant.now().plusSeconds(3600);
                String subject = claims.get("sub") != null ? String.valueOf(claims.get("sub")) : "local-user";
                return Jwt.withTokenValue(token)
                        .headers(h -> h.put("alg", "none"))
                        .claims(c -> c.putAll(claims))
                        .subject(subject)
                        .issuedAt(iat)
                        .expiresAt(exp)
                        .build();
            } catch (Exception e) {
                throw new IllegalArgumentException("No se pudo decodificar JWT local: " + e.getMessage(), e);
            }
        };
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) return value;
        return value + "====".substring(mod);
    }
}
