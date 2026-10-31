package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioNotificacionEscalamiento;
import com.workflow.financiero.aplicacion.servicio.ServicioReasignacionTarea;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ejecuta los temporizadores de notificación y reasignación con
 * ManagementService y comprueba la persistencia, la idempotencia y los casos
 * en que no corresponde actuar.
 */
class PruebaEscalamientoTest extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioNotificacionEscalamiento servicioNotificacion;

    @Autowired
    ServicioReasignacionTarea servicioReasignacion;

    @Autowired
    PropiedadesAprobacion propiedadesAprobacion;

    @AfterEach
    void limpiarRoles() {
        quitarRol("demo", "GERENTE");
        activarUsuario("gerente");
    }

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

    @Test
    @DisplayName("El timer de 48 horas asigna la tarea al superior")
    void timerDeReasignacionAsignaAlSuperior() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        assertThat(buscarTimer(procesoId, "reasignarSupervisor48h"))
            .isNotNull();

        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        assertThat(asignadoTarea(tareaId)).isEqualTo("gerente");
        assertThat(asignadoMotor(procesoId, "tareaSupervisor"))
            .isEqualTo("gerente");
        assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarDecisionesDeRevision(revisionId)).isZero();
        assertThat(tiposEventoDeRevision(revisionId))
            .contains("TAREA_REASIGNADA");

        Map<String, Object> notificacion =
            notificacionDeRevisionTipo(revisionId, "REASIGNACION_48H");
        assertThat(notificacion.get("noti_destinatario_usua_id"))
            .isEqualTo(idUsuario("gerente"));
        assertThat(notificacion.get("noti_destinatario_rol_codigo")).isNull();
        assertThat((String) notificacion.get("noti_contenido"))
            .contains("SUPERVISOR", "gerente", "48 horas");

        JsonNode api = notificaciones("gerente");
        assertThat(api.size()).isEqualTo(1);
        assertThat(api.get(0).get("tipo").asText())
            .isEqualTo("REASIGNACION_48H");
        assertThat(api.get(0).get("idTarea").asText()).isEqualTo(tareaId);
    }

    @Test
    @DisplayName("Los tres roles se reasignan a su superior configurado")
    void losTresRolesSeReasignanASusSuperiores() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        ejecutarTimer(procesoId, "reasignarSupervisor48h");
        ejecutarTimer(procesoId, "reasignarGerente48h");
        ejecutarTimer(procesoId, "reasignarRiesgos48h");

        assertThat(asignadoTarea(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR")))
            .isEqualTo("gerente");
        assertThat(asignadoTarea(
            idTareaAprobacionDeRevision(revisionId, "GERENTE")))
            .isEqualTo("gerente_general");
        assertThat(asignadoTarea(
            idTareaAprobacionDeRevision(revisionId, "RIESGOS")))
            .isEqualTo("jefe_riesgos");

        assertThat(asignadoMotor(procesoId, "tareaSupervisor"))
            .isEqualTo("gerente");
        assertThat(asignadoMotor(procesoId, "tareaGerente"))
            .isEqualTo("gerente_general");
        assertThat(asignadoMotor(procesoId, "tareaRiesgos"))
            .isEqualTo("jefe_riesgos");

        assertThat(rolesTareasNegocioDeRevision(revisionId))
            .containsExactly("GERENTE", "RIESGOS", "SUPERVISOR");
        assertThat(estadosTareasDeRevision(revisionId))
            .containsExactly(
                "GERENTE:PENDIENTE",
                "RIESGOS:PENDIENTE",
                "SUPERVISOR:PENDIENTE"
            );
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "TAREA_REASIGNADA"))
            .isEqualTo(3);
        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "REASIGNACION_48H"))
            .isEqualTo(3);
        assertThat(procesoActivo(procesoId)).isTrue();

        assertThat(notificaciones("gerente").size()).isEqualTo(1);
        assertThat(notificaciones("gerente_general").size()).isEqualTo(1);
        assertThat(notificaciones("jefe_riesgos").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("El delegado resuelve la tarea reasignada")
    void delegadoResuelveTareaReasignada() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        JsonNode bandejaGerente = bandeja("gerente");
        assertThat(bandejaGerente.size()).isEqualTo(1);
        assertThat(bandejaGerente.get(0).get("rol").asText())
            .isEqualTo("SUPERVISOR");
        assertThat(bandejaGerente.get(0).get("asignadoA").asText())
            .isEqualTo("gerente");
        assertThat(bandeja("supervisor").size()).isZero();

        JsonNode respuesta = leer(aprobar(tareaId, "gerente", null)
            .andExpect(status().isOk())
            .andReturn());

        assertThat(respuesta.get("delegado").asBoolean()).isTrue();
        assertThat(respuesta.get("estadoRevision").asText())
            .isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(rolRegistrado(solicitudId, "gerente"))
            .isEqualTo("SUPERVISOR");

        Map<String, Object> decision = decisionDeTarea(tareaId);
        assertThat(decision.get("deciapro_delegado")).isEqualTo(true);
        assertThat(decision.get("deciapro_actor_usua_id"))
            .isEqualTo(idUsuario("gerente"));
        assertThat(observaciones(revisionId)).isNotEmpty();
    }

    @Test
    @DisplayName("Sin superior elegible se registra el fallo y sigue pendiente")
    void sinSuperiorElegibleRegistraFallo() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        desactivarUsuario("gerente");
        try {
            ejecutarTimer(procesoId, "reasignarSupervisor48h");

            assertThat(asignadoTarea(tareaId)).isNull();
            assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
            assertThat(procesoActivo(procesoId)).isTrue();
            assertThat(contarDecisionesDeRevision(revisionId)).isZero();
            assertThat(tiposEventoDeRevision(revisionId))
                .contains("REASIGNACION_FALLIDA")
                .doesNotContain("TAREA_REASIGNADA");

            Map<String, Object> notificacion =
                notificacionDeRevisionTipo(revisionId, "REASIGNACION_FALLIDA");
            assertThat(notificacion.get("noti_destinatario_rol_codigo"))
                .isEqualTo("ADMINISTRADOR");
            assertThat(notificacion.get("noti_destinatario_usua_id")).isNull();
            assertThat((String) notificacion.get("noti_contenido"))
                .contains("SUPERVISOR", "GERENTE");

            JsonNode api = notificaciones("admin");
            assertThat(api.size()).isEqualTo(1);
            assertThat(api.get(0).get("tipo").asText())
                .isEqualTo("REASIGNACION_FALLIDA");

            assertThat(contarNotificacionesDeRevisionTipo(
                revisionId, "REASIGNACION_48H"))
                .isZero();
        } finally {
            activarUsuario("gerente");
        }
    }

    @Test
    @DisplayName("La reasignación es idempotente ante reintentos")
    void reasignacionEsIdempotente() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        assertThat(servicioReasignacion.reasignar(
            idMotor, "corr-reasignacion")).isTrue();
        assertThat(servicioReasignacion.reasignar(
            idMotor, "corr-reasignacion")).isFalse();

        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        assertThat(asignadoTarea(tareaId)).isEqualTo("gerente");
        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "REASIGNACION_48H"))
            .isEqualTo(1);
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "TAREA_REASIGNADA"))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("El fallo de reasignación no duplica la notificación")
    void falloDeReasignacionEsIdempotente() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        desactivarUsuario("gerente");
        try {
            assertThat(servicioReasignacion.reasignar(
                idMotor, "corr-fallo")).isFalse();
            assertThat(servicioReasignacion.reasignar(
                idMotor, "corr-fallo")).isFalse();

            assertThat(contarNotificacionesDeRevisionTipo(
                revisionId, "REASIGNACION_FALLIDA"))
                .isEqualTo(1);
            assertThat(contarEventosDeRevisionTipo(
                revisionId, "REASIGNACION_FALLIDA"))
                .isEqualTo(1);
            assertThat(estadoTarea(idTareaAprobacionDeRevision(
                revisionId, "SUPERVISOR")))
                .isEqualTo("PENDIENTE");
        } finally {
            activarUsuario("gerente");
        }
    }

    @Test
    @DisplayName("Una aprobación antes del timer no reasigna la tarea")
    void aprobacionAntesDelTimerNoReasigna() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(buscarTimer(procesoId, "reasignarSupervisor48h")).isNull();
        assertThat(servicioReasignacion.reasignar(
            idMotor, "corr-tardia")).isFalse();

        assertThat(asignadoTarea(tareaId)).isNull();
        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "REASIGNACION_48H"))
            .isZero();
        assertThat(contarEventosDeRevisionTipo(
            revisionId, "TAREA_REASIGNADA"))
            .isZero();
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Las notificaciones de 24 y 48 horas son distintas")
    void notificaciones24hY48hSonDistintas() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        ejecutarTimer(procesoId, "notificarSupervisor24h");
        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "ESCALAMIENTO_24H"))
            .isEqualTo(1);
        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "REASIGNACION_48H"))
            .isEqualTo(1);

        Map<String, Object> escalamiento =
            notificacionDeRevisionTipo(revisionId, "ESCALAMIENTO_24H");
        assertThat(escalamiento.get("noti_destinatario_rol_codigo"))
            .isEqualTo("SUPERVISOR");
        assertThat(escalamiento.get("noti_destinatario_usua_id")).isNull();

        Map<String, Object> reasignacion =
            notificacionDeRevisionTipo(revisionId, "REASIGNACION_48H");
        assertThat(reasignacion.get("noti_destinatario_usua_id"))
            .isEqualTo(idUsuario("gerente"));
        assertThat(reasignacion.get("noti_destinatario_rol_codigo")).isNull();

        assertThat(asignadoTarea(idTareaAprobacionDeRevision(
            revisionId, "SUPERVISOR")))
            .isEqualTo("gerente");
    }

    @Test
    @DisplayName("La notificación de 24 horas tardía no se crea tras reasignar")
    void notificacion24hTardiaNoSeCrea() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        assertThat(buscarTimer(procesoId, "notificarSupervisor24h"))
            .isNotNull();
        ejecutarTimer(procesoId, "notificarSupervisor24h");

        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "ESCALAMIENTO_24H"))
            .isZero();
        assertThat(contarNotificacionesDeRevisionTipo(
            revisionId, "REASIGNACION_48H"))
            .isEqualTo(1);
        assertThat(asignadoTarea(idTareaAprobacionDeRevision(
            revisionId, "SUPERVISOR")))
            .isEqualTo("gerente");
    }

    @Test
    @DisplayName("El superior reasignado no aprueba dos roles funcionales")
    void superiorReasignadoNoApruebaDosRoles() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaSupervisor =
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");
        String tareaGerente =
            idTareaAprobacionDeRevision(revisionId, "GERENTE");

        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        aprobar(tareaSupervisor, "gerente", null)
            .andExpect(status().isOk());

        aprobar(tareaGerente, "gerente", null)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("SEGREGACION_FUNCIONES"));

        assertThat(estadoTarea(tareaGerente)).isEqualTo("PENDIENTE");
        assertThat(rolRegistrado(solicitudId, "gerente"))
            .isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("El creador asignado como superior no puede aprobar")
    void creadorComoSuperiorAsignadoNoPuedeAprobar() throws Exception {
        otorgarRol("demo", "GERENTE");
        try {
            JsonNode creada = crear("demo", 25_000_000, "NORMAL");
            String solicitudId = creada.get("id").asText();
            String revisionId = creada.get("idRevision").asText();
            String procesoId = identificadorProceso(solicitudId);
            String tareaId =
                idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

            ejecutarTimer(procesoId, "reasignarSupervisor48h");

            assertThat(asignadoTarea(tareaId)).isEqualTo("demo");

            aprobar(tareaId, "demo", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo")
                    .value("APROBACION_NO_PERMITIDA"));

            assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
            assertThat(contarDecisionesDeRevision(revisionId)).isZero();
        } finally {
            quitarRol("demo", "GERENTE");
        }
    }

    private JsonNode bandeja(String usuario) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(
                get(RUTA_TAREAS), token(usuario)))
            .andExpect(status().isOk())
            .andReturn();
        return leer(resultado);
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

    private Map<String, Object> notificacionDeRevisionTipo(
        String revisionId,
        String tipo
    ) {
        return jdbcTemplate.queryForMap(
            "SELECT noti_tipo, noti_estado, noti_contenido, "
                + "noti_tareapro_id, noti_destinatario_usua_id, "
                + "noti_destinatario_rol_codigo "
                + "FROM notificacion "
                + "WHERE noti_tareapro_id IN ("
                + "SELECT tareapro_id FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ?) "
                + "AND noti_tipo = ?",
            UUID.fromString(revisionId),
            tipo
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

    private long contarNotificacionesDeRevisionTipo(
        String revisionId,
        String tipo
    ) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notificacion "
                + "WHERE noti_tareapro_id IN ("
                + "SELECT tareapro_id FROM tarea_aprobacion "
                + "WHERE tareapro_revisoli_id = ?) "
                + "AND noti_tipo = ?",
            Long.class,
            UUID.fromString(revisionId),
            tipo
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
}
