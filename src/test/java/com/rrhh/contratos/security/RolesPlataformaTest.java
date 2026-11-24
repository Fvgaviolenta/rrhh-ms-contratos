package com.rrhh.contratos.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolesPlataformaTest {

    @Test
    void adminPlataformaSeReconoce() {
        assertEquals(Roles.ADMIN_PLATAFORMA, Roles.authorityFromClaim("AdminPlataforma"));
        assertEquals("AdminPlataforma", Roles.claimFromAuthority(Roles.ADMIN_PLATAFORMA));
    }
}
