package com.rrhh.contratos.security;

public final class Roles {
    public static final String OPERADOR_SAAS = "ROLE_OPERADOR_SAAS";
    public static final String SUPERADMIN = "ROLE_SUPERADMIN";
    public static final String ADMIN_RRHH = "ROLE_ADMIN_RRHH";
    public static final String JEFATURA = "ROLE_JEFATURA";
    public static final String TRABAJADOR = "ROLE_TRABAJADOR";

    private Roles() {}

    public static String authorityFromClaim(String roleClaim) {
        if (roleClaim == null) {
            return TRABAJADOR;
        }
        return switch (roleClaim.trim()) {
            case "OperadorSaaS", "ROLE_OPERADOR_SAAS" -> OPERADOR_SAAS;
            case "SuperAdmin", "SUPERADMIN", "ROLE_SUPERADMIN" -> SUPERADMIN;
            case "Admin de RRHH", "ADMIN_RRHH", "ROLE_ADMIN_RRHH" -> ADMIN_RRHH;
            case "Jefatura", "JEFATURA", "ROLE_JEFATURA" -> JEFATURA;
            case "Trabajador", "TRABAJADOR", "ROLE_TRABAJADOR" -> TRABAJADOR;
            default -> TRABAJADOR;
        };
    }

    public static String claimFromAuthority(String authority) {
        return switch (authority) {
            case OPERADOR_SAAS -> "OperadorSaaS";
            case SUPERADMIN -> "SuperAdmin";
            case ADMIN_RRHH -> "Admin de RRHH";
            case JEFATURA -> "Jefatura";
            default -> "Trabajador";
        };
    }
}
