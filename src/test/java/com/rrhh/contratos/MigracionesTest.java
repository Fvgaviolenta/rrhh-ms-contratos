package com.rrhh.contratos;

import com.rrhh.contratos.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MigracionesTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextoArrancaConMigracionesEnMysql() {
        Integer aplicadas = jdbc.queryForObject(
                "select count(*) from flyway_history_contratos where success = 1", Integer.class);
        Integer contratos = jdbc.queryForObject("select count(*) from contratos_contrato", Integer.class);
        assertThat(aplicadas).isEqualTo(3);
        assertThat(contratos).isZero();
    }
}
