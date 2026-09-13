package com.rrhh.contratos.security;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    private static final ThreadLocal<AuthenticatedUser> CURRENT = new ThreadLocal<>();

    public void set(AuthenticatedUser user) { CURRENT.set(user); }

    public AuthenticatedUser require() {
        AuthenticatedUser user = CURRENT.get();
        if (user == null || user.tenantId() == null) {
            throw new IllegalStateException("No hay contexto de tenant");
        }
        return user;
    }

    public void clear() { CURRENT.remove(); }

    public record AuthenticatedUser(String tenantId, String userId, String email, String role, String trabajadorId, String cognitoSub) {}
}
