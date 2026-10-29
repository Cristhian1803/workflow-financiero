package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaEsquemaTest extends PruebaIntegracionBase {

    private static final String ESQUEMA = "public";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Las tablas base usan el prefijo derivado de su nombre")
    void tablasBaseUsanElPrefijoDeSuNombre() {
        Map<String, String> tablas = Map.of(
            "moneda", "mone",
            "tipo_operacion", "tipooper",
            "perfil_riesgo", "perfries",
            "rol", "rol",
            "usuario", "usua",
            "usuario_rol", "usurol",
            "solicitud_financiera", "solifina",
            "revision_solicitud", "revisoli",
            "evento_solicitud", "evensoli",
            "registro_actor_rol", "regiactorol"
        );

        tablas.forEach((tabla, prefijo) -> {
            List<String> columnas = columnas(tabla);
            assertThat(columnas).as("columnas de %s", tabla).isNotEmpty();
            assertThat(columnas)
                .as("prefijo %s en %s", prefijo, tabla)
                .allMatch(columna -> columna.startsWith(prefijo + "_"));
        });
    }

    @Test
    @DisplayName("Las claves primarias, foráneas, únicas y checks están definidas")
    void restriccionesPrincipalesEstanDefinidas() {
        List<Restriccion> restricciones = List.of(
            new Restriccion("pk_moneda", "p"),
            new Restriccion("pk_tipo_operacion", "p"),
            new Restriccion("pk_perfil_riesgo", "p"),
            new Restriccion("pk_rol", "p"),
            new Restriccion("pk_usuario", "p"),
            new Restriccion("pk_usuario_rol", "p"),
            new Restriccion("pk_solicitud_financiera", "p"),
            new Restriccion("pk_revision_solicitud", "p"),
            new Restriccion("pk_evento_solicitud", "p"),
            new Restriccion("pk_registro_actor_rol", "p"),
            new Restriccion("uq_usuario_usuario", "u"),
            new Restriccion("uq_solicitud_revision_actual", "u"),
            new Restriccion("uq_revision_numero", "u"),
            new Restriccion("uq_revision_solicitud_id", "u"),
            new Restriccion("fk_usuario_perfil", "f"),
            new Restriccion("fk_usuario_rol_usuario", "f"),
            new Restriccion("fk_usuario_rol_rol", "f"),
            new Restriccion("fk_solicitud_solicitante", "f"),
            new Restriccion("fk_solicitud_revision_actual", "f"),
            new Restriccion("fk_revision_solicitud", "f"),
            new Restriccion("fk_revision_tipo_operacion", "f"),
            new Restriccion("fk_revision_moneda", "f"),
            new Restriccion("fk_evento_solicitud", "f"),
            new Restriccion("fk_evento_actor", "f"),
            new Restriccion("fk_evento_rol", "f"),
            new Restriccion("fk_evento_revision_solicitud", "f"),
            new Restriccion("fk_registro_actor_rol_solicitud", "f"),
            new Restriccion("fk_registro_actor_rol_usuario", "f"),
            new Restriccion("fk_registro_actor_rol_rol", "f"),
            new Restriccion("ck_moneda_decimales", "c"),
            new Restriccion("ck_revision_monto", "c"),
            new Restriccion("ck_revision_nivel_riesgo", "c"),
            new Restriccion("ck_revision_estado", "c"),
            new Restriccion("ck_evento_estado_anterior", "c"),
            new Restriccion("ck_evento_estado_nuevo", "c")
        );

        restricciones.forEach(restriccion -> {
            Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = ? AND contype = ?",
                Long.class,
                restriccion.nombre(),
                restriccion.tipo()
            );
            assertThat(total)
                .as("restricción %s de tipo %s", restriccion.nombre(), restriccion.tipo())
                .isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("Los catálogos tienen las semillas iniciales")
    void catalogosTienenSemillasIniciales() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM moneda WHERE mone_codigo = 'PYG' "
                + "AND mone_activa = TRUE AND mone_decimales = 0",
            Long.class
        )).isEqualTo(1L);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT tipooper_sensible FROM tipo_operacion WHERE tipooper_codigo = 'NORMAL'",
            Boolean.class
        )).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT tipooper_sensible FROM tipo_operacion WHERE tipooper_codigo = 'SENSIBLE'",
            Boolean.class
        )).isTrue();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM perfil_riesgo "
                + "WHERE perfries_codigo IN ('NORMAL', 'ALTO') AND perfries_activo = TRUE",
            Long.class
        )).isEqualTo(2L);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rol", Long.class))
            .isEqualTo(10L);
        assertThat(esRolAprobador("SUPERVISOR")).isTrue();
        assertThat(esRolAprobador("GERENTE")).isTrue();
        assertThat(esRolAprobador("RIESGOS")).isTrue();
        assertThat(esRolAprobador("SOLICITANTE")).isFalse();
        assertThat(esRolAprobador("ADMINISTRADOR")).isFalse();
    }

    @Test
    @DisplayName("Los instantes y montos usan los tipos definidos")
    void instantesYMontosUsanLosTiposDefinidos() {
        List<String[]> instantes = List.of(
            new String[] {"usuario", "usua_creado_en"},
            new String[] {"usuario", "usua_actualizado_en"},
            new String[] {"usuario_rol", "usurol_asignado_en"},
            new String[] {"solicitud_financiera", "solifina_creado_en"},
            new String[] {"solicitud_financiera", "solifina_actualizado_en"},
            new String[] {"revision_solicitud", "revisoli_creado_en"},
            new String[] {"revision_solicitud", "revisoli_actualizado_en"},
            new String[] {"evento_solicitud", "evensoli_creado_en"},
            new String[] {"registro_actor_rol", "regiactorol_fecha"}
        );

        instantes.forEach(par -> assertThat(tipoColumna(par[0], par[1]))
            .as("%s.%s", par[0], par[1])
            .isEqualTo("timestamp with time zone"));

        assertThat(tipoColumna("revision_solicitud", "revisoli_monto"))
            .isEqualTo("numeric");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT numeric_precision FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'revision_solicitud' "
                + "AND column_name = 'revisoli_monto'",
            Integer.class,
            ESQUEMA
        )).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT numeric_scale FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'revision_solicitud' "
                + "AND column_name = 'revisoli_monto'",
            Integer.class,
            ESQUEMA
        )).isZero();
    }

    @Test
    @DisplayName("La solicitud no duplica el estado de su revisión actual")
    void solicitudNoDuplicaElEstado() {
        assertThat(columnas("solicitud_financiera"))
            .noneMatch(columna -> columna.contains("estado"));
    }

    @Test
    @DisplayName("Los checks de estado y riesgo contienen los valores esperados")
    void checksDeEstadoYRiesgoContienenLosValoresEsperados() {
        assertThat(definicionRestriccion("ck_revision_estado"))
            .contains("RECHAZADA", "CANCELADA", "EJECUTADA");
        assertThat(definicionRestriccion("ck_revision_nivel_riesgo"))
            .contains("BAJO", "MEDIO", "ALTO");
        assertThat(definicionRestriccion("ck_revision_monto"))
            .contains("revisoli_monto", ">");
        assertThat(definicionRestriccion("ck_moneda_decimales"))
            .contains("mone_decimales", ">=");
    }

    private List<String> columnas(String tabla) {
        return jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
            String.class,
            ESQUEMA,
            tabla
        );
    }

    private String tipoColumna(String tabla, String columna) {
        return jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            String.class,
            ESQUEMA,
            tabla,
            columna
        );
    }

    private boolean esRolAprobador(String codigo) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT rol_aprobador FROM rol WHERE rol_codigo = ?",
            Boolean.class,
            codigo
        ));
    }

    private String definicionRestriccion(String nombre) {
        return jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
            String.class,
            nombre
        );
    }

    private record Restriccion(String nombre, String tipo) {
    }
}
