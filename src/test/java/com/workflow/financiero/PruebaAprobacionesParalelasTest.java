package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.infraestructura.ejecucion.AdaptadorEjecucionControlada.TipoObservacion;
import java.util.Map;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba las aprobaciones paralelas, la espera del riesgo externo y que la
 * ejecución controlada solo se prepare cuando el proceso está autorizado.
 */
class PruebaAprobacionesParalelasTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("Gerente y Riesgos se resuelven en cualquier orden y esperan el riesgo externo")
    void gerenteLuegoRiesgosEsperaRiesgoExterno() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder("tareaSupervisor", "tareaRiesgos");

        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactly("tareaSupervisor");

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId)).isEmpty();
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        enviarRiesgoExterno(procesoId, true, "ALTO");

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(observaciones(revisionId))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
        assertThat(tiposEvento(solicitudId))
            .contains(
                "EVALUACION_EXTERNA_PENDIENTE",
                "EVALUACION_EXTERNA_RESUELTA"
            );
    }

    @Test
    @DisplayName("Riesgos y Gerente se pueden resolver en el orden inverso")
    void riesgosLuegoGerenteEsperaRiesgoExterno() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder("tareaSupervisor", "tareaGerente");

        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactly("tareaSupervisor");

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        enviarRiesgoExterno(procesoId, true, "ALTO");

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(observaciones(revisionId))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
    }

    @Test
    @DisplayName("Supervisor puede aprobar antes que Gerente y Riesgos")
    void supervisorPrimeroEsperaRiesgoExterno() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder("tareaGerente", "tareaRiesgos");

        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);

        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        enviarRiesgoExterno(procesoId, true, "ALTO");

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
    }

    @Test
    @DisplayName("Una sola aprobación no prepara la ejecución")
    void unaSolaAprobacionNoPreparaEjecucion() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());

        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder("tareaGerente", "tareaRiesgos");
    }

    @Test
    @DisplayName("Cien millones con riesgo alto finaliza sin espera externa")
    void cienMillonesAltoFinalizaSinEsperaExterna() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "SENSIBLE");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder(
                "tareaSupervisor", "tareaGerente", "tareaRiesgos");

        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);

        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();
        assertThat(observaciones(revisionId))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
    }

    @Test
    @DisplayName("Cien millones sin riesgo alto espera la segunda aprobación")
    void cienMillonesNormalEsperaLaSegundaAprobacion() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(rolesTareasMotor(procesoId))
            .containsExactlyInAnyOrder("tareaSupervisor", "tareaGerente");

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(rolesTareasMotor(procesoId)).containsExactly("tareaGerente");

        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(observaciones(revisionId))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
    }

    @Test
    @DisplayName("Un rechazo de Riesgos cancela las otras ramas sin ejecutar")
    void rechazoDeRiesgosCancelaLasOtrasRamas() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaRiesgos = idTareaAprobacion(solicitudId, "RIESGOS");
        String tareaSupervisor = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String tareaGerente = idTareaAprobacion(solicitudId, "GERENTE");

        rechazar(tareaRiesgos, "riesgos", "Riesgo no aceptable")
            .andExpect(status().isOk());

        assertThat(estadoTarea(tareaRiesgos)).isEqualTo("RECHAZADA");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaGerente)).isEqualTo("CANCELADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(tiposEvento(solicitudId))
            .contains("RECHAZO_REGISTRADO", "TAREA_CANCELADA");
    }

    @Test
    @DisplayName("Un rechazo posterior conserva la aprobación ya registrada")
    void rechazoPosteriorConservaAprobacionRegistrada() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaGerente = idTareaAprobacion(solicitudId, "GERENTE");
        String tareaSupervisor = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String tareaRiesgos = idTareaAprobacion(solicitudId, "RIESGOS");

        aprobar(tareaGerente, "gerente", null)
            .andExpect(status().isOk());
        rechazar(tareaRiesgos, "riesgos", "Riesgo no aceptable")
            .andExpect(status().isOk());

        assertThat(estadoTarea(tareaGerente)).isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaRiesgos)).isEqualTo("RECHAZADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarDecisiones(solicitudId)).isEqualTo(2);
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(tiposEvento(solicitudId))
            .contains(
                "APROBACION_REGISTRADA",
                "RECHAZO_REGISTRADO",
                "TAREA_CANCELADA"
            );
    }

    @Test
    @DisplayName("Un fixture inválido mantiene el proceso esperando")
    void fixtureInvalidoMantieneLaEspera() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "SUPERVISOR"), "supervisor", null)
            .andExpect(status().isOk());
        aprobar(idTareaAprobacion(solicitudId, "GERENTE"), "gerente", null)
            .andExpect(status().isOk());
        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
            .andExpect(status().isOk());

        assertThatThrownBy(() -> enviarRiesgoExterno(procesoId, false, "ALTO"))
            .hasStackTraceContaining("riesgo externo");
        assertThatThrownBy(() -> enviarRiesgoExterno(procesoId, true, "BAJO"))
            .hasStackTraceContaining("no puede ser menor");

        assertPendienteSinEjecucion(revisionId, procesoId);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        enviarRiesgoExterno(procesoId, true, "ALTO");

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
    }

    @Test
    @DisplayName("La verificación final bloquea una tarea sin decisión persistida")
    void verificacionFinalBloqueaSinDecisionPersistida() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        Task tarea = tareaMotor(procesoId, "tareaSupervisor");

        assertThatThrownBy(() -> taskService.complete(
            tarea.getId(),
            Map.of("decisionSupervisor", "APROBADA")))
            .hasStackTraceContaining("No se autoriza la ejecución");

        assertThat(tareaMotor(procesoId, "tareaSupervisor")).isNotNull();
        assertThat(estadoTarea(idTareaAprobacion(solicitudId, "SUPERVISOR")))
            .isEqualTo("PENDIENTE");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarDecisiones(solicitudId)).isZero();
        assertThat(observaciones(revisionId)).isEmpty();
    }

    @Test
    @DisplayName("Enviar el mensaje a un proceso que no lo espera falla")
    void mensajeSinSuscripcionFalla() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String procesoId = identificadorProceso(creada.get("id").asText());

        assertThatThrownBy(() -> enviarRiesgoExterno(procesoId, true, "ALTO"))
            .hasStackTraceContaining("no espera el mensaje");
    }

    private void assertPendienteSinEjecucion(
        String revisionId,
        String procesoId
    ) {
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(observaciones(revisionId)).isEmpty();
    }
}
