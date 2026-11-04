package com.rrhh.contratos;

import com.rrhh.contratos.config.TestJwtConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MigracionesTest {
    private static final String ESQUEMA = "relleno_v4";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextoArrancaConMigracionesEnMysql() {
        Integer aplicadas = jdbc.queryForObject(
                "select count(*) from flyway_history_contratos where success = 1", Integer.class);
        Integer contratos = jdbc.queryForObject("select count(*) from contratos_contrato", Integer.class);
        assertThat(aplicadas).isEqualTo(4);
        assertThat(contratos).isZero();
    }

    @Test
    void migracionV4RellenaEstadoDesdeActivo() throws SQLException {
        DataSource root = rootDelContenedor();
        JdbcTemplate rootJdbc = new JdbcTemplate(root);
        rootJdbc.execute("drop database if exists " + ESQUEMA);

        flyway(root, "3").migrate();
        rootJdbc.update("insert into " + ESQUEMA + ".contratos_contrato (id, tenant_id, trabajador_id, salario_base, "
                + "tipo_contrato, fecha_inicio, activo, creado_en) values "
                + "('c0000000-0000-0000-0000-000000000001', 't', 'w1', 900000, 'INDEFINIDO', '2025-01-01', 1, now()), "
                + "('c0000000-0000-0000-0000-000000000002', 't', 'w2', 800000, 'INDEFINIDO', '2024-01-01', 0, now())");

        flyway(root, "latest").migrate();

        Map<String, Object> activo = rootJdbc.queryForMap("select estado, jornada_horas_semanales from " + ESQUEMA
                + ".contratos_contrato where id = 'c0000000-0000-0000-0000-000000000001'");
        Map<String, Object> inactivo = rootJdbc.queryForMap("select estado, jornada_horas_semanales from " + ESQUEMA
                + ".contratos_contrato where id = 'c0000000-0000-0000-0000-000000000002'");
        assertThat(activo.get("estado")).isEqualTo("FIRMADO");
        assertThat(inactivo.get("estado")).isEqualTo("FINALIZADO");
        assertThat(activo.get("jornada_horas_semanales")).isNull();
        assertThat(inactivo.get("jornada_horas_semanales")).isNull();
        Integer tablaDocumento = rootJdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = ? and table_name = 'contratos_documento'", Integer.class, ESQUEMA);
        assertThat(tablaDocumento).isEqualTo(1);

        rootJdbc.execute("drop database " + ESQUEMA);
    }

    private static Flyway flyway(DataSource ds, String target) {
        return Flyway.configure().dataSource(ds).schemas(ESQUEMA).table("flyway_history_contratos")
                .target(target).load();
    }

    /** El usuario de la aplicacion solo tiene permisos sobre su base; root (misma clave en Testcontainers) crea el esquema nuevo. */
    private DataSource rootDelContenedor() throws SQLException {
        String url;
        try (Connection c = dataSource.getConnection()) {
            url = c.getMetaData().getURL();
        }
        return new DriverManagerDataSource(url, "root", "test");
    }
}
