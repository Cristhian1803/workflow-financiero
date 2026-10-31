package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.servicio.CrearSolicitudComando;
import com.workflow.financiero.aplicacion.servicio.ServicioNotificacionEscalamiento;
import com.workflow.financiero.aplicacion.servicio.ServicioReasignacionTarea;
import com.workflow.financiero.aplicacion.servicio.ServicioReenvioSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.SolicitudDetalle;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas concurrentes de aprobación, segregación, reenvío y temporizadores.
 * Cada competidor usa una transacción independiente y una barrera de inicio.
 */
class PruebaConcurrenciaAprobacionTest extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioUsuarioActual servicioUsuarioActual;

    @Autowired
    ServicioReenvioSolicitud servicioReenvio;

    @Autowired
    ServicioNotificacionEscalamiento servicioNotificacion;

    @Autowired
    ServicioReasignacionTarea servicioReasignacion;

    @RepeatedTest(3)
    @DisplayName("Dos aprobadores de la misma tarea producen una sola decisión")
    void dosAprobadoresProducenUnaSolaDecision() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        String tokenSupervisor = token("supervisor");
        String tokenGerente = token("gerente");
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ConcurrentLinkedQueue<RespuestaHttp> respuestas = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            ejecutor.submit(() -> ejecutarAprobacion(
                tareaId, tokenSupervisor, inicio, fin, respuestas, errores));
            ejecutor.submit(() -> ejecutarAprobacion(
                tareaId, tokenGerente, inicio, fin, respuestas, errores));
            inicio.countDown();
            assertThat(fin.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(errores).isEmpty();
        assertThat(respuestas).hasSize(2);
        assertThat(respuestas.stream().filter(r -> r.estado() == 200)).hasSize(1);
        RespuestaHttp conflicto = respuestas.stream()
            .filter(r -> r.estado() == 409)
            .findFirst()
            .orElseThrow();
        assertThat(objectMapper.readTree(conflicto.cuerpo()).get("codigo").asText())
            .isIn("TAREA_YA_RESUELTA", "CONFLICTO_DATOS");

        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "APROBACION_REGISTRADA")).isEqualTo(1);
        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(rolesTareasMotor(procesoId)).isEmpty();
    }

    @RepeatedTest(3)
    @DisplayName("El mismo actor no aprueba dos roles en paralelo")
    void mismoActorNoApruebaDosRolesEnParalelo() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaSupervisor = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String tareaGerente = idTareaAprobacion(solicitudId, "GERENTE");

        String tokenGerente = token("gerente");
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ConcurrentLinkedQueue<RespuestaHttp> respuestas = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            ejecutor.submit(() -> ejecutarAprobacion(
                tareaSupervisor, tokenGerente, inicio, fin, respuestas, errores));
            ejecutor.submit(() -> ejecutarAprobacion(
                tareaGerente, tokenGerente, inicio, fin, respuestas, errores));
            inicio.countDown();
            assertThat(fin.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(errores).isEmpty();
        assertThat(respuestas).hasSize(2);
        assertThat(respuestas.stream().filter(r -> r.estado() == 200)).hasSize(1);
        RespuestaHttp rechazo = respuestas.stream()
            .filter(r -> r.estado() == 403)
            .findFirst()
            .orElseThrow();
        assertThat(objectMapper.readTree(rechazo.cuerpo()).get("codigo").asText())
            .isEqualTo("SEGREGACION_FUNCIONES");

        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "APROBACION_REGISTRADA")).isEqualTo(1);
        assertThat(contarRegistrosActorRol(solicitudId, "gerente")).isEqualTo(1);
        assertThat(estadoRevision(revisionId)).isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadosTareasDeRevision(revisionId).stream()
            .filter(estado -> estado.endsWith(":APROBADA"))
            .count()).isEqualTo(1);
        assertThat(estadosTareasDeRevision(revisionId).stream()
            .filter(estado -> estado.endsWith(":PENDIENTE"))
            .count()).isEqualTo(1);
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(rolesTareasMotor(procesoId)).hasSize(1);
    }

    @Test
    @DisplayName("Dos reenvíos concurrentes crean una sola revisión nueva")
    void dosReenviosConcurrentesCreanUnaSolaRevision() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revisionId, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        UsuarioActual demo = servicioUsuarioActual.obtenerActual("demo");
        CrearSolicitudComando comando = new CrearSolicitudComando(
            "NORMAL",
            BigDecimal.valueOf(25_000_000),
            "PYG",
            "Pago corregido",
            "Se ajusta el monto a la documentación"
        );

        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ConcurrentLinkedQueue<SolicitudDetalle> exitos = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        exitos.add(servicioReenvio.reenviar(
                            UUID.fromString(solicitudId),
                            demo,
                            comando,
                            "corr-concurrente"
                        ));
                    } catch (Throwable error) {
                        errores.add(error);
                    } finally {
                        fin.countDown();
                    }
                });
            }
            inicio.countDown();
            assertThat(fin.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(exitos).hasSize(1);
        assertThat(errores).hasSize(1);
        assertThat(errores.peek()).isInstanceOfAny(
            TransicionEstadoInvalidaException.class,
            DataIntegrityViolationException.class
        );
        assertThat(contarRevisiones(solicitudId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Una aprobación anterior impide notificaciones y reasignaciones tardías")
    void aprobacionAnteriorImpideTimersTardios() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        aprobar(tareaId, "supervisor", null).andExpect(status().isOk());

        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(buscarTimer(procesoId, "notificarSupervisor24h")).isNull();
        assertThat(buscarTimer(procesoId, "reasignarSupervisor48h")).isNull();
        assertThat(servicioNotificacion.notificarEscalamiento(
            idMotor, "corr-tardia")).isFalse();
        assertThat(servicioReasignacion.reasignar(
            idMotor, "corr-tardia")).isFalse();
        assertThat(contarNotificacionesDeRevisionTotal(revisionId)).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "NOTIFICACION_ESCALAMIENTO")).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_REASIGNADA")).isZero();
        assertThat(asignadoTarea(tareaId)).isNull();
    }

    @Test
    @DisplayName("Una reasignación anterior condiciona la aprobación posterior")
    void reasignacionAnteriorCondicionaAprobacion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        ejecutarTimer(procesoId, "reasignarSupervisor48h");
        ejecutarTimer(procesoId, "notificarSupervisor24h");

        assertThat(asignadoTarea(tareaId)).isEqualTo("gerente");
        assertThat(asignadoMotor(procesoId, "tareaSupervisor")).isEqualTo("gerente");
        assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
        assertThat(contarNotificacionesTipoDeRevision(
            revisionId, "REASIGNACION_48H")).isEqualTo(1);
        assertThat(contarNotificacionesTipoDeRevision(
            revisionId, "ESCALAMIENTO_24H")).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_REASIGNADA")).isEqualTo(1);

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isNotFound());
        assertThat(contarDecisionesDeRevision(revisionId)).isZero();

        JsonNode respuesta = leer(aprobar(tareaId, "gerente", null)
            .andExpect(status().isOk())
            .andReturn());
        assertThat(respuesta.get("delegado").asBoolean()).isTrue();
        assertThat(estadoTarea(tareaId)).isEqualTo("APROBADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(rolRegistrado(solicitudId, "gerente"))
            .isEqualTo("SUPERVISOR");
        assertThat(procesoFinalizado(procesoId)).isTrue();
    }

    @RepeatedTest(3)
    @DisplayName("El timer de reasignación y la aprobación concurrentes son consistentes")
    void timerYAprobacionConcurrentes() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String tokenSupervisor = token("supervisor");

        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        AtomicReference<Integer> estadoAprobacion = new AtomicReference<>();
        AtomicReference<Throwable> errorAprobacion = new AtomicReference<>();
        AtomicReference<Throwable> errorTimer = new AtomicReference<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            ejecutor.submit(() -> {
                try {
                    inicio.await();
                    MvcResult resultado = aprobarConToken(
                        tareaId, tokenSupervisor).andReturn();
                    estadoAprobacion.set(resultado.getResponse().getStatus());
                } catch (Throwable error) {
                    errorAprobacion.set(error);
                } finally {
                    fin.countDown();
                }
            });
            ejecutor.submit(() -> {
                try {
                    inicio.await();
                    ejecutarTimer(procesoId, "reasignarSupervisor48h");
                } catch (Throwable error) {
                    errorTimer.set(error);
                } finally {
                    fin.countDown();
                }
            });
            inicio.countDown();
            assertThat(fin.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(errorAprobacion.get()).isNull();
        String estado = estadoTarea(tareaId);
        assertThat(estado).isIn("APROBADA", "PENDIENTE");
        assertThat(contarNotificacionesTipoDeRevision(
            revisionId, "REASIGNACION_48H")).isBetween(0L, 1L);
        assertThat(contarDecisionesDeRevision(revisionId)).isBetween(0L, 1L);

        if ("APROBADA".equals(estado)) {
            assertThat(estadoAprobacion.get()).isEqualTo(200);
            assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
            assertThat(contarNotificacionesTipoDeRevision(
                revisionId, "REASIGNACION_48H")).isZero();
            assertThat(asignadoTarea(tareaId)).isNull();
            assertThat(procesoFinalizado(procesoId)).isTrue();
            if (errorTimer.get() != null) {
                assertThat(errorTimer.get()).isInstanceOfAny(
                    IllegalStateException.class,
                    FlowableObjectNotFoundException.class,
                    FlowableOptimisticLockingException.class
                );
            }
        } else {
            assertThat(estadoAprobacion.get()).isEqualTo(404);
            assertThat(asignadoTarea(tareaId)).isEqualTo("gerente");
            assertThat(contarDecisionesDeRevision(revisionId)).isZero();
            assertThat(contarNotificacionesTipoDeRevision(
                revisionId, "REASIGNACION_48H")).isEqualTo(1);
            assertThat(errorTimer.get()).isNull();
            assertThat(procesoActivo(procesoId)).isTrue();
        }
    }

    private void ejecutarAprobacion(
        String tareaId,
        String token,
        CountDownLatch inicio,
        CountDownLatch fin,
        ConcurrentLinkedQueue<RespuestaHttp> respuestas,
        ConcurrentLinkedQueue<Throwable> errores
    ) {
        try {
            inicio.await();
            MvcResult resultado = aprobarConToken(tareaId, token).andReturn();
            respuestas.add(new RespuestaHttp(
                resultado.getResponse().getStatus(),
                resultado.getResponse().getContentAsString()
            ));
        } catch (Throwable error) {
            errores.add(error);
        } finally {
            fin.countDown();
        }
    }

    private ResultActions aprobarConToken(String tareaId, String token)
        throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("comentario", null);
        return mockMvc.perform(conToken(
                post(RUTA_TAREAS + "/" + tareaId + "/aprobar"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    private record RespuestaHttp(int estado, String cuerpo) {
    }
}
