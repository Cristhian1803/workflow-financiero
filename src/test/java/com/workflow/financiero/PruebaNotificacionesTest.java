package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cubre la visibilidad, el orden y el contrato de la API de notificaciones.
 */
class PruebaNotificacionesTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("Una notificación por rol es visible para el rol destinatario")
    void notificacionPorRolVisibleParaElRol() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        UUID tareaId = UUID.fromString(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"));

        insertarNotificacion(
            tareaId,
            null,
            "SUPERVISOR",
            "ESCALAMIENTO_24H",
            "La tarea de aprobación de SUPERVISOR lleva 24 horas pendiente",
            ahora()
        );

        JsonNode supervisor = notificaciones("supervisor");
        assertThat(supervisor.size()).isEqualTo(1);
        assertThat(supervisor.get(0).get("tipo").asText())
            .isEqualTo("ESCALAMIENTO_24H");
        assertThat(supervisor.get(0).get("idSolicitud").asText())
            .isEqualTo(solicitudId);
        assertThat(supervisor.get(0).get("revision").asInt()).isEqualTo(1);

        assertThat(notificaciones("riesgos").size()).isZero();
    }

    @Test
    @DisplayName("Una notificación directa es visible para su usuario")
    void notificacionDirectaVisibleParaElUsuario() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        UUID tareaId = UUID.fromString(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"));

        insertarNotificacion(
            tareaId,
            idUsuario("gerente"),
            null,
            "TAREA_ASIGNADA",
            "Se le asignó una tarea de aprobación",
            ahora()
        );

        JsonNode gerente = notificaciones("gerente");
        assertThat(gerente.size()).isEqualTo(1);
        assertThat(gerente.get(0).get("tipo").asText())
            .isEqualTo("TAREA_ASIGNADA");

        assertThat(notificaciones("supervisor").size()).isZero();
    }

    @Test
    @DisplayName("Sin token la API de notificaciones devuelve 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get(RUTA_NOTIFICACIONES))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Las notificaciones se ordenan por fecha descendente")
    void ordenaPorFechaDescendente() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        UUID tareaId = UUID.fromString(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"));

        insertarNotificacion(
            tareaId,
            null,
            "SUPERVISOR",
            "TAREA_ASIGNADA",
            "Notificación antigua",
            ahora().minusHours(2)
        );
        insertarNotificacion(
            tareaId,
            null,
            "SUPERVISOR",
            "ESCALAMIENTO_24H",
            "Notificación nueva",
            ahora()
        );

        JsonNode lista = notificaciones("supervisor");
        assertThat(lista.size()).isEqualTo(2);
        assertThat(lista.get(0).get("contenido").asText())
            .isEqualTo("Notificación nueva");
        assertThat(lista.get(1).get("contenido").asText())
            .isEqualTo("Notificación antigua");
    }

    @Test
    @DisplayName("Una notificación por usuario y rol no se duplica")
    void combinaUsuarioYRolSinDuplicar() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        UUID tareaId = UUID.fromString(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"));

        insertarNotificacion(
            tareaId,
            idUsuario("supervisor"),
            "SUPERVISOR",
            "ESCALAMIENTO_24H",
            "Notificación con dos destinatarios",
            ahora()
        );

        assertThat(notificaciones("supervisor").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("La API no expone datos internos de la notificación")
    void noExponeDatosInternos() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();
        UUID tareaId = UUID.fromString(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"));

        insertarNotificacion(
            tareaId,
            null,
            "SUPERVISOR",
            "ESCALAMIENTO_24H",
            "Contenido visible",
            ahora()
        );

        JsonNode notificacion = notificaciones("supervisor").get(0);
        assertThat(notificacion.has("destinatarioUsuario")).isFalse();
        assertThat(notificacion.has("destinatarioRol")).isFalse();
        assertThat(notificacion.has("usuario")).isFalse();
        assertThat(notificacion.has("claveHash")).isFalse();
        assertThat(notificacion.has("idTarea")).isTrue();
        assertThat(notificacion.has("idSolicitud")).isTrue();
    }

    @Test
    @DisplayName("Un usuario sin notificaciones recibe una lista vacía")
    void usuarioSinNotificacionesRecibeListaVacia() throws Exception {
        assertThat(notificaciones("demo2").size()).isZero();
    }

    private void insertarNotificacion(
        UUID tareaId,
        UUID destinatarioUsuarioId,
        String destinatarioRol,
        String tipo,
        String contenido,
        OffsetDateTime creadoEn
    ) {
        jdbcTemplate.update(
            "INSERT INTO notificacion ("
                + "noti_id, noti_tareapro_id, noti_destinatario_usua_id, "
                + "noti_destinatario_rol_codigo, noti_tipo, noti_contenido, "
                + "noti_estado, noti_creado_en, noti_leida_en) "
                + "VALUES (?, CAST(? AS UUID), CAST(? AS UUID), CAST(? AS VARCHAR), "
                + "?, ?, 'PENDIENTE', ?, NULL)",
            UUID.randomUUID(),
            tareaId,
            destinatarioUsuarioId,
            destinatarioRol,
            tipo,
            contenido,
            creadoEn
        );
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
