package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.servicio.ResultadoCancelacionSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioCancelacionSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.aplicacion.simulador.ModoFalloSimulador;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.excepcion.CancelacionNoPermitidaException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cubre la cancelación con la API definitiva, la compensación básica de una
 * reserva y el cierre del proceso. Los casos de confirmación incierta y
 * liberación fallida recuperable se completan en la siguiente iteración.
 */
class PruebaCancelacionCompensacionTest
    extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioCancelacionSolicitud servicioCancelacion;

    @Autowired
    ServicioUsuarioActual servicioUsuarioActual;

    @Test
    @DisplayName("Cancelar durante la aprobación deja CANCELADA y cierra el proceso")
    void cancelarDuranteAprobacion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        cancelar(solicitudId, "demo", "El solicitante desiste")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoRevision").value("CANCELADA"))
            .andExpect(jsonPath("$.compensacionPendiente").value(false));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaId)).isEqualTo("CANCELADA");
        assertThat(procesoActivo(procesoId)).isFalse();
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_CONFIRMADA")).isZero();
        assertThat(actorDeEvento(revisionId, "CANCELACION_SOLICITADA"))
            .isEqualTo("demo");

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TAREA_YA_RESUELTA"));
    }

    @Test
    @DisplayName("Una aprobación parcial puede completarse con una cancelación")
    void aprobacionParcialLuegoCancelacion() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaSupervisor = idTareaAprobacion(
            solicitudId, "SUPERVISOR");
        String tareaGerente = idTareaAprobacion(solicitudId, "GERENTE");

        aprobar(tareaSupervisor, "supervisor", null)
            .andExpect(status().isOk());
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");

        cancelar(solicitudId, "demo", "Ya no se requiere la operación")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoRevision").value("CANCELADA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaGerente)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
        assertThat(procesoFinalizado(procesoId)).isTrue();
    }

    @Test
    @DisplayName("Cancelar antes de reservar deja CANCELADA sin efectos del proveedor")
    void cancelarAntesDeReservar() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");

        cancelar(solicitudId, "demo", "Desiste antes de ejecutar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoRevision").value("CANCELADA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CANCELADA");
        assertThat(proveedorDirecto.reservas()).isZero();
        assertThat(proveedorDirecto.liberaciones()).isZero();
        assertThat(contarOperacionesSimuladas("RESERVA")).isZero();
        assertThat(contarOperacionesSimuladas("LIBERACION")).isZero();
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_CONFIRMADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancelar con reserva y confirmación no despachada libera y deja CANCELADA")
    void cancelarConReservaYCompensacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("FALLIDA");
        assertThat(estadoOperacionSimulada(claveReserva(solicitudId, revisionId)))
            .isEqualTo("RESERVADA");
        assertThat(contarOperacionesSimuladas("CONFIRMACION")).isZero();
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");

        fallos.limpiar();

        cancelar(solicitudId, "demo", "Se cancela antes de confirmar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoRevision").value("CANCELADA"))
            .andExpect(jsonPath("$.compensacionPendiente").value(false));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("COMPENSADA");
        assertThat(estadoOperacionSimulada(claveReserva(solicitudId, revisionId)))
            .isEqualTo("LIBERADA");
        assertThat(estadoOperacionSimulada(claveLiberacion(solicitudId, revisionId)))
            .isEqualTo("LIBERADA");
        assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        assertThat(contarOperacionesSimuladas("LIBERACION")).isEqualTo(1);
        assertThat(estadoIncidente(revisionId)).isEqualTo("RESUELTO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_CONFIRMADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancelar después de una confirmación efectiva responde 409 y no libera")
    void cancelarDespuesDeConfirmacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");

        cancelar(solicitudId, "demo", "Intento tardío de cancelación")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CANCELACION_NO_PERMITIDA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(proveedorDirecto.liberaciones()).isZero();
        assertThat(contarOperacionesSimuladas("LIBERACION")).isZero();
    }

    @Test
    @DisplayName("Una cancelación duplicada no repite transición ni efecto")
    void cancelacionDuplicada() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        cancelar(solicitudId, "demo", "Primera cancelación")
            .andExpect(status().isOk());
        cancelar(solicitudId, "demo", "Segunda cancelación")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CANCELACION_NO_PERMITIDA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Solo el dueño o un administrador pueden cancelar por la API")
    void rolesDeCancelacion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        cancelar(solicitudId, "demo2", "No es el dueño")
            .andExpect(status().isNotFound());
        cancelar(solicitudId, "supervisor", "No es el dueño")
            .andExpect(status().isForbidden());

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");

        cancelar(solicitudId, "admin", "Cancelación administrativa")
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("Una revisión rechazada no se cancela")
    void revisionRechazadaNoSeCancela() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        rechazar(tareaId, "supervisor", "No corresponde")
            .andExpect(status().isOk());
        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");

        cancelar(solicitudId, "demo", "No corresponde")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CANCELACION_NO_PERMITIDA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isZero();
    }

    @Test
    @DisplayName("Una liberación fallida deja COMPENSANDO y no declara CANCELADA")
    void liberacionFallidaQuedaPendiente() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        assertThat(estadoOperacionSimulada(claveReserva(solicitudId, revisionId)))
            .isEqualTo("RESERVADA");

        fallos.limpiar();
        fallos.configurar(
            claveLiberacion(solicitudId, revisionId),
            ModoFalloSimulador.LIBERACION_FALLIDA
        );

        cancelar(solicitudId, "demo", "Se intenta compensar")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estadoRevision").value("COMPENSANDO"))
            .andExpect(jsonPath("$.compensacionPendiente").value(true));

        assertThat(estadoRevision(revisionId)).isEqualTo("COMPENSANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("COMPENSANDO");
        assertThat(estadoOperacionSimulada(claveReserva(solicitudId, revisionId)))
            .isEqualTo("RESERVADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_PENDIENTE")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una confirmación en tránsito deja la cancelación pendiente sin liberar")
    void confirmacionEnTransitoQuedaPendiente() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        jdbcTemplate.update(
            "UPDATE intento_ejecucion SET inteejec_estado = 'ENVIANDO' "
                + "WHERE inteejec_revisoli_id = ?",
            UUID.fromString(revisionId)
        );

        cancelar(solicitudId, "demo", "Resultado todavía incierto")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estadoRevision").value("COMPENSANDO"))
            .andExpect(jsonPath("$.compensacionPendiente").value(true));

        assertThat(estadoRevision(revisionId)).isEqualTo("COMPENSANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("COMPENSANDO");
        assertThat(estadoCompensacion(revisionId)).isEqualTo("PENDIENTE");
        assertThat(despachoEnCursoCompensacion(revisionId)).isTrue();
        assertThat(proveedorDirecto.liberaciones()).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_PENDIENTE")).isEqualTo(1);
    }

    @Test
    @DisplayName("El motivo de la cancelación es obligatorio en la API")
    void motivoObligatorioEnLaApi() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        cancelar(solicitudId, "demo", " ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isZero();
    }

    @RepeatedTest(3)
    @DisplayName("Dos cancelaciones concurrentes producen un solo efecto")
    void cancelacionesConcurrentes() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        UsuarioActual demo = servicioUsuarioActual.obtenerActual("demo");
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ConcurrentLinkedQueue<ResultadoCancelacionSolicitud> exitos =
            new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores =
            new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        exitos.add(servicioCancelacion.cancelar(
                            UUID.fromString(solicitudId),
                            demo,
                            "Cancelación concurrente",
                            "corr-cancelacion"
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
        assertThat(errores.peek())
            .isInstanceOf(CancelacionNoPermitidaException.class);
        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("El subproceso de cancelación interrumpe tareas y timers y termina el proceso")
    void subprocesoDeCancelacionInterrumpeYTermina() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(buscarTimer(procesoId, "notificarSupervisor24h"))
            .isNotNull();

        puertoProceso.enviarMensaje(
            "CANCELACION_SOLICITADA",
            procesoId,
            Map.of("motivo", "Spike de cancelación")
        );

        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .count()).isZero();
        assertThat(buscarTimer(procesoId, "notificarSupervisor24h")).isNull();
        assertThat(buscarTimer(procesoId, "reasignarSupervisor48h")).isNull();
    }

    @Test
    @DisplayName("Si la confirmación llega después, la ejecución gana y la cancelación se rechaza")
    void confirmacionPosteriorRechazaCancelacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        jdbcTemplate.update(
            "UPDATE intento_ejecucion SET inteejec_estado = 'ENVIANDO' "
                + "WHERE inteejec_revisoli_id = ?",
            UUID.fromString(revisionId)
        );

        cancelar(solicitudId, "demo", "Se intenta cancelar en tránsito")
            .andExpect(status().isAccepted());
        assertThat(estadoCompensacion(revisionId)).isEqualTo("PENDIENTE");

        proveedorDirecto.reservar(operacion(
            solicitudId, revisionId, AccionFinanciera.RESERVA));
        proveedorDirecto.confirmar(operacion(
            solicitudId, revisionId, AccionFinanciera.CONFIRMACION));

        ejecutarCompensacion();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(estadoCompensacion(revisionId)).isEqualTo("RECHAZADA");
        assertThat(proveedorDirecto.liberaciones()).isZero();
        assertThat(contarOperacionesSimuladas("LIBERACION")).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_RECHAZADA")).isEqualTo(1);
        assertThat(procesoFinalizado(procesoId)).isTrue();

        cancelar(solicitudId, "demo", "Segundo intento")
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una liberación con respuesta perdida se recupera por la misma clave")
    void liberacionConRespuestaPerdidaSeRecupera() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        fallos.limpiar();
        fallos.configurar(
            claveLiberacion(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );

        cancelar(solicitudId, "demo", "Se compensa con respuesta perdida")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoRevision").value("CANCELADA"));

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("COMPENSADA");
        assertThat(estadoCompensacion(revisionId)).isEqualTo("LIBERADA");
        assertThat(estadoOperacionSimulada(
            claveReserva(solicitudId, revisionId))).isEqualTo("LIBERADA");
        assertThat(estadoOperacionSimulada(
            claveLiberacion(solicitudId, revisionId))).isEqualTo("LIBERADA");
        assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        assertThat(contarOperacionesSimuladas("LIBERACION")).isEqualTo(1);
        assertThat(estadoIncidente(revisionId)).isEqualTo("RESUELTO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_CONFIRMADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("La liberación fallida se reintenta y termina en revisión manual")
    void liberacionFallidaReintentaHastaManualYSeResuelve() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        fallos.limpiar();
        fallos.configurar(
            claveLiberacion(solicitudId, revisionId),
            ModoFalloSimulador.LIBERACION_FALLIDA
        );

        cancelar(solicitudId, "demo", "La liberación falla")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estadoRevision").value("COMPENSANDO"));

        for (int intento = 0; intento < 3; intento++) {
            ejecutarCompensacion();
        }

        assertThat(estadoRevision(revisionId)).isEqualTo("COMPENSANDO");
        assertThat(estadoCompensacion(revisionId))
            .isEqualTo("REVISION_MANUAL");
        assertThat(intentosCompensacion(revisionId)).isEqualTo(3);
        assertThat(estadoIncidente(revisionId, "COMPENSACION"))
            .isEqualTo("ABIERTO");
        assertThat(incidenteReintentable(revisionId, "COMPENSACION")).isTrue();
        assertThat(proveedorDirecto.liberaciones()).isZero();
        assertThat(contarOperacionesSimuladas("LIBERACION")).isZero();

        fallos.limpiar();
        String incidenteId = idIncidente(revisionId, "COMPENSACION");
        reintentarIncidenteApi(
            incidenteId, "operaciones", "Proveedor recuperado")
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoCompensacion(revisionId)).isEqualTo("LIBERADA");
        assertThat(estadoIncidente(revisionId, "COMPENSACION"))
            .isEqualTo("RESUELTO");
        assertThat(reintentosIncidente(revisionId, "COMPENSACION"))
            .isEqualTo(1);
        assertThat(actorIncidente(revisionId, "COMPENSACION"))
            .isEqualTo("operaciones");
        assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        assertThat(contarOperacionesSimuladas("LIBERACION")).isEqualTo(1);
        assertThat(estadoOperacionSimulada(
            claveReserva(solicitudId, revisionId))).isEqualTo("LIBERADA");
    }

    @Test
    @DisplayName("Un reinicio durante la compensación retoma la liberación sin duplicar")
    void reinicioDuranteCompensacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        fallos.limpiar();
        fallos.configurar(
            claveLiberacion(solicitudId, revisionId),
            ModoFalloSimulador.LIBERACION_FALLIDA
        );
        cancelar(solicitudId, "demo", "Compensación que se interrumpe")
            .andExpect(status().isAccepted());

        jdbcTemplate.update(
            "UPDATE intento_compensacion "
                + "SET intecomp_estado = 'LIBERANDO', "
                + "intecomp_reclamado_en = now() - interval '1 minute' "
                + "WHERE intecomp_revisoli_id = ?",
            UUID.fromString(revisionId)
        );
        fallos.limpiar();

        ejecutarCompensacion();

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoCompensacion(revisionId)).isEqualTo("LIBERADA");
        assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        assertThat(contarOperacionesSimuladas("LIBERACION")).isEqualTo(1);
        assertThat(estadoOperacionSimulada(
            claveReserva(solicitudId, revisionId))).isEqualTo("LIBERADA");
    }

    @Test
    @DisplayName("La cancelación duplicada con reserva produce una sola compensación")
    void cancelacionDuplicadaConCompensacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        fallos.limpiar();

        cancelar(solicitudId, "demo", "Primera cancelación")
            .andExpect(status().isOk());
        cancelar(solicitudId, "demo", "Segunda cancelación")
            .andExpect(status().isConflict());

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarCompensaciones(revisionId)).isEqualTo(1);
        assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "COMPENSACION_CONFIRMADA")).isEqualTo(1);
    }

    @Test
    @DisplayName("El proceso sigue activo mientras la compensación está pendiente")
    void procesoPermaneceActivoMientrasCompensacionPendiente() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        proveedorDirecto.reservar(operacion(
            solicitudId, revisionId, AccionFinanciera.RESERVA));
        jdbcTemplate.update(
            "UPDATE intento_ejecucion SET inteejec_estado = 'FALLIDA' "
                + "WHERE inteejec_revisoli_id = ?",
            UUID.fromString(revisionId)
        );
        fallos.configurar(
            claveLiberacion(solicitudId, revisionId),
            ModoFalloSimulador.LIBERACION_FALLIDA
        );

        cancelar(solicitudId, "demo", "Compensación pendiente")
            .andExpect(status().isAccepted());

        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(estadoCompensacion(revisionId)).isEqualTo("PENDIENTE");

        fallos.limpiar();
        ejecutarCompensacion();

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(taskService.createTaskQuery()
            .processInstanceId(procesoId)
            .count()).isZero();
    }

    @Test
    @DisplayName("La cancelación gana antes de reservar y el trabajador no confirma")
    void cancelacionGanaAntesDeReservar() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        proveedorDirecto.pausarAntesDeReservar();
        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        Future<Integer> futuro = ejecutor.submit(this::ejecutarTrabajador);
        try {
            proveedorDirecto.esperarPausaReserva();

            cancelar(solicitudId, "demo", "La cancelación gana la carrera")
                .andExpect(status().isAccepted());
            assertThat(estadoCompensacion(revisionId))
                .isEqualTo("PENDIENTE");
            assertThat(despachoEnCursoCompensacion(revisionId)).isTrue();

            proveedorDirecto.liberarReserva();
            futuro.get(20, TimeUnit.SECONDS);

            assertThat(proveedorDirecto.reservas()).isEqualTo(1);
            assertThat(proveedorDirecto.confirmaciones()).isZero();
            assertThat(despachoEnCursoCompensacion(revisionId)).isFalse();

            ejecutarCompensacion();

            assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
            assertThat(estadoCompensacion(revisionId))
                .isEqualTo("LIBERADA");
            assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
            assertThat(contarOperacionesSimuladas("CONFIRMACION")).isZero();
        } finally {
            proveedorDirecto.liberarReserva();
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("La cancelación gana después de reservar y antes de confirmar")
    void cancelacionGanaAntesDeConfirmar() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        proveedorDirecto.pausarAntesDeConfirmar();
        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        Future<Integer> futuro = ejecutor.submit(this::ejecutarTrabajador);
        try {
            proveedorDirecto.esperarPausaConfirmacion();

            cancelar(solicitudId, "demo", "La cancelación libera primero")
                .andExpect(status().isOk());

            assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
            assertThat(estadoCompensacion(revisionId))
                .isEqualTo("LIBERADA");
            assertThat(estadoOperacionSimulada(
                claveReserva(solicitudId, revisionId)))
                .isEqualTo("LIBERADA");

            proveedorDirecto.liberarConfirmacion();
            futuro.get(20, TimeUnit.SECONDS);

            assertThat(proveedorDirecto.confirmaciones()).isEqualTo(1);
            assertThat(contarOperacionesSimuladas("CONFIRMACION")).isZero();
            assertThat(proveedorDirecto.liberaciones()).isEqualTo(1);
        } finally {
            proveedorDirecto.liberarConfirmacion();
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("El despacho gana la carrera y la cancelación se rechaza")
    void despachoGanaAntesDeLaCancelacion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        proveedorDirecto.pausarDespuesDeConfirmar();
        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        Future<Integer> futuro = ejecutor.submit(this::ejecutarTrabajador);
        try {
            proveedorDirecto.esperarEfectoConfirmacion();

            cancelar(solicitudId, "demo", "Llega tarde")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                    .value("CANCELACION_NO_PERMITIDA"));

            assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
            assertThat(estadoCompensacion(revisionId))
                .isEqualTo("RECHAZADA");
            assertThat(proveedorDirecto.liberaciones()).isZero();

            proveedorDirecto.liberarEfectoConfirmacion();
            futuro.get(20, TimeUnit.SECONDS);

            assertThat(contarOperacionesSimuladas("CONFIRMACION"))
                .isEqualTo(1);
            assertThat(contarOperacionesSimuladas("LIBERACION")).isZero();
        } finally {
            proveedorDirecto.liberarEfectoConfirmacion();
            ejecutor.shutdownNow();
        }
    }

    private OperacionFinanciera operacion(
        String solicitudId,
        String revisionId,
        AccionFinanciera accion
    ) {
        return OperacionFinanciera.crear(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            accion,
            BigDecimal.valueOf(5_000_000),
            "PYG",
            "NORMAL"
        );
    }

    private String actorDeEvento(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT u.usua_usuario FROM evento_solicitud e "
                + "LEFT JOIN usuario u ON u.usua_id = e.evensoli_actor_usua_id "
                + "WHERE e.evensoli_revisoli_id = ? AND e.evensoli_tipo = ?",
            String.class,
            UUID.fromString(revisionId),
            tipo
        );
    }

    private String claveReserva(String solicitudId, String revisionId) {
        return ClavesOperacionFinanciera.reserva(
            UUID.fromString(solicitudId), UUID.fromString(revisionId));
    }

    private String claveConfirmacion(String solicitudId, String revisionId) {
        return ClavesOperacionFinanciera.confirmacion(
            UUID.fromString(solicitudId), UUID.fromString(revisionId));
    }

    private String claveLiberacion(String solicitudId, String revisionId) {
        return ClavesOperacionFinanciera.liberacion(
            UUID.fromString(solicitudId), UUID.fromString(revisionId));
    }
}
