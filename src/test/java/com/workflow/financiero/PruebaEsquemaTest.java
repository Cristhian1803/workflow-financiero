package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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
        Map<String, String> tablas = new LinkedHashMap<>();
        tablas.put("moneda", "mone");
        tablas.put("tipo_operacion", "tipooper");
        tablas.put("perfil_riesgo", "perfries");
        tablas.put("rol", "rol");
        tablas.put("usuario", "usua");
        tablas.put("usuario_rol", "usurol");
        tablas.put("solicitud_financiera", "solifina");
        tablas.put("revision_solicitud", "revisoli");
        tablas.put("evento_solicitud", "evensoli");
        tablas.put("registro_actor_rol", "regiactorol");
        tablas.put("tarea_aprobacion", "tareapro");
        tablas.put("decision_aprobacion", "deciapro");
        tablas.put("evaluacion_riesgo", "evalries");
        tablas.put("intento_ejecucion", "inteejec");
        tablas.put("evento_externo", "evenexte");
        tablas.put("notificacion", "noti");
        tablas.put("registro_idempotencia", "regiidem");

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
            new Restriccion("ck_evento_estado_nuevo", "c"),
            new Restriccion("pk_tarea_aprobacion", "p"),
            new Restriccion("pk_decision_aprobacion", "p"),
            new Restriccion("pk_evaluacion_riesgo", "p"),
            new Restriccion("pk_intento_ejecucion", "p"),
            new Restriccion("pk_evento_externo", "p"),
            new Restriccion("pk_notificacion", "p"),
            new Restriccion("pk_registro_idempotencia", "p"),
            new Restriccion("uq_tarea_revision_rol", "u"),
            new Restriccion("uq_tarea_motor", "u"),
            new Restriccion("uq_decision_tarea", "u"),
            new Restriccion("uq_intento_revision", "u"),
            new Restriccion("uq_intento_clave", "u"),
            new Restriccion("uq_evento_externo", "u"),
            new Restriccion("uq_idempotencia_clave", "u"),
            new Restriccion("fk_tarea_revision", "f"),
            new Restriccion("fk_tarea_rol", "f"),
            new Restriccion("fk_tarea_asignado", "f"),
            new Restriccion("fk_decision_tarea", "f"),
            new Restriccion("fk_decision_actor", "f"),
            new Restriccion("fk_evaluacion_revision", "f"),
            new Restriccion("fk_intento_revision", "f"),
            new Restriccion("fk_evento_externo_solicitud", "f"),
            new Restriccion("fk_evento_externo_revision", "f"),
            new Restriccion("fk_evento_externo_revision_solicitud", "f"),
            new Restriccion("fk_notificacion_tarea", "f"),
            new Restriccion("fk_notificacion_usuario", "f"),
            new Restriccion("fk_notificacion_rol", "f"),
            new Restriccion("fk_idempotencia_usuario", "f"),
            new Restriccion("ck_tarea_estado", "c"),
            new Restriccion("ck_decision_tipo", "c"),
            new Restriccion("ck_evaluacion_origen", "c"),
            new Restriccion("ck_evaluacion_nivel", "c"),
            new Restriccion("ck_notificacion_estado", "c"),
            new Restriccion("ck_notificacion_destinatario", "c")
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
            new String[] {"registro_actor_rol", "regiactorol_fecha"},
            new String[] {"tarea_aprobacion", "tareapro_fecha_vencimiento"},
            new String[] {"tarea_aprobacion", "tareapro_creado_en"},
            new String[] {"tarea_aprobacion", "tareapro_actualizado_en"},
            new String[] {"decision_aprobacion", "deciapro_fecha"},
            new String[] {"evaluacion_riesgo", "evalries_creado_en"},
            new String[] {"intento_ejecucion", "inteejec_creado_en"},
            new String[] {"intento_ejecucion", "inteejec_actualizado_en"},
            new String[] {"evento_externo", "evenexte_recibido_en"},
            new String[] {"notificacion", "noti_creado_en"},
            new String[] {"notificacion", "noti_leida_en"},
            new String[] {"registro_idempotencia", "regiidem_expira_en"},
            new String[] {"registro_idempotencia", "regiidem_creado_en"}
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

    @Test
    @DisplayName("Las columnas opcionales de las tablas nuevas aceptan nulos")
    void columnasOpcionalesAceptanNulos() {
        List<String[]> opcionales = List.of(
            new String[] {"tarea_aprobacion", "tareapro_identificador_tarea_motor"},
            new String[] {"tarea_aprobacion", "tareapro_asignado_usua_id"},
            new String[] {"tarea_aprobacion", "tareapro_fecha_vencimiento"},
            new String[] {"tarea_aprobacion", "tareapro_actualizado_en"},
            new String[] {"decision_aprobacion", "deciapro_motivo"},
            new String[] {"evaluacion_riesgo", "evalries_resultado"},
            new String[] {"evaluacion_riesgo", "evalries_correlacion"},
            new String[] {"intento_ejecucion", "inteejec_referencia_externa"},
            new String[] {"intento_ejecucion", "inteejec_actualizado_en"},
            new String[] {"evento_externo", "evenexte_solifina_id"},
            new String[] {"evento_externo", "evenexte_revisoli_id"},
            new String[] {"notificacion", "noti_tareapro_id"},
            new String[] {"notificacion", "noti_destinatario_usua_id"},
            new String[] {"notificacion", "noti_destinatario_rol_codigo"},
            new String[] {"notificacion", "noti_leida_en"},
            new String[] {"registro_idempotencia", "regiidem_codigo_respuesta"},
            new String[] {"registro_idempotencia", "regiidem_cuerpo_respuesta"}
        );

        opcionales.forEach(par -> assertThat(esNullable(par[0], par[1]))
            .as("%s.%s debe aceptar nulos", par[0], par[1])
            .isTrue());

        assertThat(esNullable("tarea_aprobacion", "tareapro_estado")).isFalse();
        assertThat(esNullable("decision_aprobacion", "deciapro_decision")).isFalse();
        assertThat(esNullable("evaluacion_riesgo", "evalries_origen")).isFalse();
        assertThat(esNullable("intento_ejecucion", "inteejec_estado")).isFalse();
        assertThat(esNullable("evento_externo", "evenexte_proveedor")).isFalse();
        assertThat(esNullable("notificacion", "noti_tipo")).isFalse();
        assertThat(esNullable("registro_idempotencia", "regiidem_clave")).isFalse();
    }

    @Test
    @DisplayName("Los índices de consulta de las tablas nuevas están definidos")
    void indicesDeConsultaEstanDefinidos() {
        List<String> indices = List.of(
            "idx_tarea_estado",
            "idx_tarea_asignado",
            "idx_decision_actor",
            "idx_evaluacion_revision",
            "idx_intento_estado",
            "idx_evento_externo_solicitud",
            "idx_evento_externo_revision",
            "idx_evento_externo_recibido",
            "idx_notificacion_usuario",
            "idx_notificacion_rol",
            "idx_notificacion_estado",
            "idx_idempotencia_expira"
        );

        indices.forEach(indice -> assertThat(contarIndice(indice))
            .as("índice %s", indice)
            .isEqualTo(1L));
    }

    @Test
    @DisplayName("Los índices únicos parciales de notificación filtran por destinatario")
    void indicesUnicosParcialesDeNotificacionEstanDefinidos() {
        String porUsuario = definicionIndice("uq_notificacion_usuario");
        assertThat(porUsuario).containsIgnoringCase("CREATE UNIQUE INDEX");
        assertThat(porUsuario).contains("noti_destinatario_usua_id");
        assertThat(porUsuario).containsIgnoringCase("WHERE");
        assertThat(porUsuario).containsIgnoringCase("IS NOT NULL");

        String porRol = definicionIndice("uq_notificacion_rol");
        assertThat(porRol).containsIgnoringCase("CREATE UNIQUE INDEX");
        assertThat(porRol).contains("noti_destinatario_rol_codigo");
        assertThat(porRol).containsIgnoringCase("WHERE");
        assertThat(porRol).containsIgnoringCase("IS NOT NULL");
    }

    @Test
    @DisplayName("Los checks de las tablas nuevas contienen los valores esperados")
    void checksDeLasTablasNuevasContienenLosValoresEsperados() {
        assertThat(definicionRestriccion("ck_tarea_estado"))
            .contains("PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA");
        assertThat(definicionRestriccion("ck_decision_tipo"))
            .contains("APROBADA", "RECHAZADA");
        assertThat(definicionRestriccion("ck_evaluacion_origen"))
            .contains("INTERNO", "EXTERNO", "MANUAL");
        assertThat(definicionRestriccion("ck_evaluacion_nivel"))
            .contains("BAJO", "MEDIO", "ALTO");
        assertThat(definicionRestriccion("ck_notificacion_estado"))
            .contains("PENDIENTE", "LEIDA");
        assertThat(definicionRestriccion("ck_notificacion_destinatario"))
            .contains("noti_destinatario_usua_id", "noti_destinatario_rol_codigo", "OR");
    }

    @Test
    @DisplayName("Los estados de ejecución y evento externo todavía no tienen check")
    void estadosFuturosNoTienenCheckTodavia() {
        assertThat(contarChecks("intento_ejecucion")).isZero();
        assertThat(contarChecks("evento_externo")).isZero();
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

    private boolean esNullable(String tabla, String columna) {
        String valor = jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            String.class,
            ESQUEMA,
            tabla,
            columna
        );
        return "YES".equals(valor);
    }

    private Long contarIndice(String nombre) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            Long.class,
            ESQUEMA,
            nombre
        );
    }

    private String definicionIndice(String nombre) {
        return jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            String.class,
            ESQUEMA,
            nombre
        );
    }

    private Long contarChecks(String tabla) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint "
                + "WHERE conrelid = to_regclass(?) AND contype = 'c'",
            Long.class,
            tabla
        );
    }

    private record Restriccion(String nombre, String tipo) {
    }
}
