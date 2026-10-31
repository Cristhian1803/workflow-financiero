package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioNotificacionEscalamiento;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ejecuta los temporizadores de notificación con ManagementService y comprueba
 * la persistencia, la idempotencia y los casos en que no corresponde notificar.
 */
class PruebaEscalamientoTest extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioNotificacionEscalamiento servicioNotificacion;

    @Autowired
    PropiedadesAprobacion propiedadesAprobacion;

    @Test
    @DisplayName("El timer de Supervisor genera una notificación visible por rol")
    void timerDeSupervisorGeneraNotificacion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        assertThat(buscarTimer(procesoId, "notificarSupervisor24h"))
            .isNotNull();

        ejecutarTimer(procesoId, "notificarSupervisor24h");

        List<Map<String, Object>> notificaciones =
            notificacionesDeRevision(revisionId);
        assertThat(notificaciones).hasSize(1);

        Map<String, Object> notificacion = notificaciones.get(0);
        assertThat(notificacion.get("noti_tipo"))
            .isEqualTo("ESCALAMIENTO_24H");
        assertThat(notificacion.get("noti_estado")).isEqualTo("PENDIENTE");
        assertThat(notificacion.get("noti_destinatario_rol_codigo"))
            .isEqualTo("SUPERVISOR");
        assertThat(notificacion.get("noti_destinatario_usua_id")).isNull();
        assertThat(notificacion.get("noti_tareapro_id").toString())
            .isEqualTo(tareaId);
        assertThat((String) notificacion.get("noti_contenido"))
            .contains("SUPERVISOR", "24 horas");

        assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(tiposEventoDeRevision(revisionId))
            .contains("NOTIFICACION_ESCALAMIENTO");

        JsonNode api = notificaciones("supervisor");
        assertThat(api.size()).isEqualTo(1);
        assertThat(api.get(0).get("tipo").asText())
            .isEqualTo("ESCALAMIENTO_24H");
        assertThat(api.get(0).get("idTarea").asText()).isEqualTo(tareaId);
        assertThat(api.get(0).get("idSolicitud").asText())
            .isEqualTo(solicitudId);
        assertThat(api.get(0).get("revision").asInt()).isEqualTo(1);
        assertThat(api.get(0).get("fechaLectura").isNull()).isTrue();

        assertThat(notificaciones("gerente").size()).isZero();
    }

    @Test
    @DisplayName("Los tres roles reciben su notificación cuando corresponde")
    void losTresRolesRecibenSuNotificacion() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        ejecutarTimer(procesoId, "notificarSupervisor24h");
        ejecutarTimer(procesoId, "notificarGerente24h");
        ejecutarTimer(procesoId, "notificarRiesgos24h");

        List<Map<String, Object>> notificaciones =
            notificacionesDeRevision(revisionId);
        assertThat(notificaciones).hasSize(3);
        assertThat(notificaciones.stream()
            .map(notificacion ->
                (String) notificacion.get("noti_destinatario_rol_codigo"))
            .toList())
            .containsExactlyInAnyOrder("SUPERVISOR", "GERENTE", "RIESGOS");

        assertThat(contarEventosDeRevisionTipo(
            revisionId, "NOTIFICACION_ESCALAMIENTO"))
            .isEqualTo(3);
        assertThat(estadosTareasDeRevision(revisionId))
            .containsExactly(
                "GERENTE:PENDIENTE",
                "RIESGOS:PENDIENTE",
                "SUPERVISOR:PENDIENTE"
            );
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();
    }

    @Test
    @DisplayName("Reintentar la notificación no crea duplicados")
    void reintentarLaNotificacionNoDuplica() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        assertThat(servicioNotificacion.notificarEscalamiento(
            idMotor, "corr-reintento")).isTrue();
        assertThat(servicioNotificacion.notificarEscalamiento(
            idMotor, "corr-reintento")).isFalse();

        ejecutarTimer(procesoId, "notificarSupervisor24h");

        assertThat(contarNotificacionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "NOTIFICACION_ESCALAMIENTO"))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("Una tarea aprobada antes del timer no genera notificación")
    void tareaAprobadaAntesDelTimerNoNotifica() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(buscarTimer(procesoId, "notificarSupervisor24h")).isNull();
        assertThat(servicioNotificacion.notificarEscalamiento(
            idMotor, "corr-tardia")).isFalse();

        assertThat(contarNotificacionesDeRevision(revisionId)).isZero();
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "NOTIFICACION_ESCALAMIENTO"))
            .isZero();
        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("Una tarea asignada antes del timer no genera notificación")
    void tareaAsignadaAntesDelTimerNoNotifica() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        jdbcTemplate.update(
            "UPDATE tarea_aprobacion SET tareapro_asignado_usua_id = ? "
                + "WHERE tareapro_id = ?",
            idUsuario("supervisor"),
            UUID.fromString(tareaId)
        );

        ejecutarTimer(procesoId, "notificarSupervisor24h");

        assertThat(contarNotificacionesDeRevision(revisionId)).isZero();
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "NOTIFICACION_ESCALAMIENTO"))
            .isZero();
        assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("La duración configurada se refleja en el proceso")
    void duracionConfiguradaSeReflejaEnElProceso() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String procesoId = identificadorProceso(creada.get("id").asText());

        assertThat(runtimeService.getVariable(
            procesoId, "duracionNotificacion"))
            .isEqualTo(propiedadesAprobacion.notificacion().toString());
        assertThat(runtimeService.getVariable(
            procesoId, "duracionReasignacion"))
            .isEqualTo(propiedadesAprobacion.reasignacion().toString());
    }

    private List<Map<String, Object>> notificacionesDeRevision(
        String revisionId
    ) {
        return jdbcTemplate.queryForList(
            "SELECT noti_tipo, noti_estado, noti_contenido, "
                + "noti_tareapro_id, noti_destinatario_usua_id, "
                + "noti_destinatario_rol_codigo "
                + "FROM notificacion "
                + "WHERE noti_tareapro_id IN ("
                + "SELECT tareapro_id FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ?)",
            UUID.fromString(revisionId)
        );
    }

    private long contarNotificacionesDeRevision(String revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notificacion "
                + "WHERE noti_tareapro_id IN ("
                + "SELECT tareapro_id FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ?)",
            Long.class,
            UUID.fromString(revisionId)
        );
    }

    private long contarEventosDeRevisionTipo(
        String revisionId,
        String tipo
    ) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_solicitud "
                + "WHERE evensoli_revisoli_id = ? AND evensoli_tipo = ?",
            Long.class,
            UUID.fromString(revisionId),
            tipo
        );
    }

    private String identificadorTareaMotor(String revisionId, String rol) {
        return jdbcTemplate.queryForObject(
            "SELECT tareapro_identificador_tarea_motor FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ? AND tareapro_rol_codigo = ?",
            String.class,
            UUID.fromString(revisionId),
            rol
        );
    }

}
