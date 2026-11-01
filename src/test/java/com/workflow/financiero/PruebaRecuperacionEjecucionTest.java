package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.servicio.ServicioReintentoIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.aplicacion.simulador.ModoFalloSimulador;
import com.workflow.financiero.dominio.excepcion.ReintentoNoPermitidoException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cubre la reconciliación de resultados inciertos, los incidentes y el
 * reintento operativo con la misma identidad financiera.
 */
class PruebaRecuperacionEjecucionTest
    extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioReintentoIncidente servicioReintento;

    @Autowired
    ServicioUsuarioActual servicioUsuarioActual;

    @Test
    @DisplayName("Una respuesta perdida en confirmación se reconcilia sin duplicar")
    void respuestaPerdidaEnConfirmacionSeReconcilia() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );

        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(proveedorDirecto.reservas()).isEqualTo(1);
        assertThat(proveedorDirecto.confirmaciones()).isEqualTo(1);
        assertThat(contarOperaciones("RESERVA")).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
        assertThat(estadoIncidente(revisionId)).isEqualTo("RESUELTO");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_CONFIRMADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "INCIDENTE_CREADO")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "INCIDENTE_RESUELTO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una respuesta perdida en reserva recupera la confirmación")
    void respuestaPerdidaEnReservaSeReconcilia() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );

        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(contarOperaciones("RESERVA")).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
        assertThat(estadoIncidente(revisionId)).isEqualTo("RESUELTO");
    }

    @Test
    @DisplayName("Consulta no disponible deja el resultado desconocido y un incidente")
    void consultaNoDisponibleDejaIncidente() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.CONSULTA_NO_DISPONIBLE
        );
        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );

        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);

        fallos.limpiar();
        ejecutarRecuperacion();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(estadoIncidente(revisionId)).isEqualTo("RESUELTO");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @Test
    @DisplayName("Al agotar intentos el proceso cierra en resultado desconocido")
    void intentosAgotadosCierranResultadoDesconocido() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.CONSULTA_NO_DISPONIBLE
        );
        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );

        ejecutarTrabajador();
        ejecutarRecuperacion();
        ejecutarRecuperacion();

        assertThat(estadoRevision(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(estadoIntento(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");

        fallos.limpiar();
        reintentarIncidente(
            idIncidente(revisionId),
            "operaciones",
            "Revisión operativa del resultado incierto"
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESUELTO"))
            .andExpect(jsonPath("$.actor").value("operaciones"));

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un error definitivo antes del efecto no confirma y permite reintento")
    void errorDefinitivoAntesDelEfecto() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );

        ejecutarTrabajador();

        assertThat(estadoIntento(revisionId)).isEqualTo("FALLIDA");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(incidenteReintentable(revisionId)).isTrue();
        assertThat(contarOperaciones("CONFIRMACION")).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_FALLIDA")).isEqualTo(1);

        fallos.limpiar();
        reintentarIncidente(
            idIncidente(revisionId),
            "operaciones",
            "Se corrigió la causa del error definitivo"
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESUELTO"));

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
        assertThat(actorIncidente(revisionId)).isEqualTo("operaciones");
        assertThat(motivoIncidente(revisionId))
            .isEqualTo("Se corrigió la causa del error definitivo");
        assertThat(reintentosIncidente(revisionId)).isEqualTo(1);
        assertThat(ultimoResultadoIncidente(revisionId))
            .isEqualTo("Ejecución confirmada por consulta");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "INCIDENTE_REINTENTANDO")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "INCIDENTE_RESUELTO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un error definitivo no se reintenta automáticamente")
    void errorDefinitivoNoSeReintentaAutomaticamente() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );

        ejecutarTrabajador();
        ejecutarRecuperacion();

        assertThat(estadoIntento(revisionId)).isEqualTo("FALLIDA");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(incidenteReintentable(revisionId)).isTrue();
        assertThat(contarOperaciones("CONFIRMACION")).isZero();
    }

    @Test
    @DisplayName("Un conflicto de idempotencia no se reintenta")
    void conflictoDeIdempotenciaNoSeReintenta() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.CONFLICTO_IDEMPOTENCIA
        );

        ejecutarTrabajador();

        assertThat(estadoIntento(revisionId)).isEqualTo("FALLIDA");
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(incidenteReintentable(revisionId)).isFalse();
        assertThat(contarOperaciones("CONFIRMACION")).isZero();

        reintentarIncidente(
            idIncidente(revisionId),
            "operaciones",
            "Intento de reintento sobre un conflicto"
        ).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("REINTENTO_NO_PERMITIDO"));

        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(contarOperaciones("CONFIRMACION")).isZero();
    }

    @Test
    @DisplayName("La API de incidentes exige rol operativo")
    void apiIncidentesExigeRolOperativo() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();

        mockMvc.perform(conToken(get(RUTA_INCIDENTES), token("demo")))
            .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get(RUTA_INCIDENTES), token("operaciones")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tipo").value("EJECUCION"));
        mockMvc.perform(conToken(get(RUTA_INCIDENTES), token("admin")))
            .andExpect(status().isOk());

        reintentarIncidente(
            idIncidente(revisionId),
            "demo",
            "No debería poder reintentar"
        ).andExpect(status().isForbidden());

        mockMvc.perform(conToken(
                post(RUTA_INCIDENTES + "/"
                    + UUID.randomUUID() + "/reintentar"),
                token("operaciones"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                    Map.of("motivo", "Incidente inexistente"))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un envío reclamado y abandonado se recupera sin duplicar")
    void envioAbandonadoSeRecupera() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        jdbcTemplate.update(
            "UPDATE intento_ejecucion SET "
                + "inteejec_estado = 'ENVIANDO', "
                + "inteejec_reclamado_en = now() - interval '1 minute', "
                + "inteejec_reclamado_por = 'trabajador-caido' "
                + "WHERE inteejec_revisoli_id = ?",
            UUID.fromString(revisionId)
        );

        ejecutarRecuperacion();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(contarOperaciones("RESERVA")).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Dos trabajadores de recuperación no duplican la confirmación")
    void dosTrabajadoresDeRecuperacionNoDuplican() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.CONSULTA_NO_DISPONIBLE
        );
        fallos.configurar(
            claveConfirmacion(solicitudId, revisionId),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );
        ejecutarTrabajador();
        fallos.limpiar();

        ConcurrentLinkedQueue<Integer> procesadas =
            new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores =
            new ConcurrentLinkedQueue<>();
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        procesadas.add(ejecutarRecuperacion());
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

        assertThat(errores).isEmpty();
        assertThat(procesadas.stream().mapToInt(Integer::intValue).sum())
            .isEqualTo(1);
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Dos reintentos manuales simultáneos producen un solo efecto")
    void dosReintentosManualesSimultaneos() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        fallos.configurar(
            claveReserva(solicitudId, revisionId),
            ModoFalloSimulador.ERROR_DEFINITIVO
        );
        ejecutarTrabajador();
        fallos.limpiar();

        String incidenteId = idIncidente(revisionId);
        UsuarioActual operaciones = servicioUsuarioActual.obtenerActual(
            "operaciones");
        ConcurrentLinkedQueue<Throwable> errores =
            new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Boolean> exitos =
            new ConcurrentLinkedQueue<>();
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        servicioReintento.reintentar(
                            UUID.fromString(incidenteId),
                            operaciones,
                            "Reintento concurrente",
                            "corr-reintento"
                        );
                        exitos.add(true);
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
            .isInstanceOf(ReintentoNoPermitidoException.class);
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @Test
    @DisplayName("El camino BPMN de resultado desconocido cierra el proceso")
    void caminoBpmnDesconocidoCierraProceso() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        puertoProceso.enviarMensaje(
            "EJECUCION_FINALIZADA",
            procesoId,
            Map.of(
                "resultadoEjecucion", "RESULTADO_DESCONOCIDO",
                "motivo", "Prueba del camino desconocido"
            )
        );

        assertThat(estadoRevision(revisionId))
            .isEqualTo("RESULTADO_DESCONOCIDO");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(estadoIncidente(revisionId)).isEqualTo("ABIERTO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_RESULTADO_DESCONOCIDO")).isEqualTo(1);
    }

    private ResultActions reintentarIncidente(
        String incidenteId,
        String usuario,
        String motivo
    ) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(conToken(
                post(RUTA_INCIDENTES + "/" + incidenteId + "/reintentar"),
                token(usuario))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(cuerpo)));
    }

    private String claveReserva(String solicitudId, String revisionId) {
        return "reserva:" + solicitudId + ":" + revisionId;
    }

    private String claveConfirmacion(String solicitudId, String revisionId) {
        return "confirmacion:" + solicitudId + ":" + revisionId;
    }

    private long contarOperaciones(String accion) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operacion_simulada "
                + "WHERE opersimu_accion = ?",
            Long.class,
            accion);
    }
}
