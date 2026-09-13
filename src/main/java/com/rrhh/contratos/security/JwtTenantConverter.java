package com.rrhh.contratos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtTenantConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final TenantContext tenantContext;
    private final String roleClaim;
    private final String tenantClaim;
    private final String userIdClaim;
    private final String trabajadorIdClaim;

    public JwtTenantConverter(
            TenantContext tenantContext,
            @Value("${rrhh.security.jwt.role-claim}") String roleClaim,
            @Value("${rrhh.security.jwt.tenant-claim}") String tenantClaim,
            @Value("${rrhh.security.jwt.user-id-claim}") String userIdClaim,
            @Value("${rrhh.security.jwt.trabajador-id-claim}") String trabajadorIdClaim
    ) {
        this.tenantContext = tenantContext;
        this.roleClaim = roleClaim;
        this.tenantClaim = tenantClaim;
        this.userIdClaim = userIdClaim;
        this.trabajadorIdClaim = trabajadorIdClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String authority = Roles.authorityFromClaim(first(jwt, roleClaim, "role"));
        tenantContext.set(new TenantContext.AuthenticatedUser(
                first(jwt, tenantClaim, "tenant_id"),
                first(jwt, userIdClaim, "user_id"),
                first(jwt, "email", "username"),
                Roles.claimFromAuthority(authority),
                first(jwt, trabajadorIdClaim, "trabajador_id"),
                jwt.getSubject()
        ));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private static String first(Jwt jwt, String... names) {
        for (String name : names) {
            String value = jwt.getClaimAsString(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
