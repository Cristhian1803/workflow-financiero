package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cubre el vencimiento de la evaluación externa, la tarea manual, la
 * consolidación del riesgo, el recálculo del plan y las carreras principales.
 */
class PruebaRecuperacionRiesgoTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("El vencimiento crea una tarea manual y no duplica aprobaciones")
    void vencimientoCreaTareaManual() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        assertThat(estadoRevision(datos.revisionId()))
            .isEqualTo("REVISION_MANUAL");
        assertThat(estadoEvaluacionRiesgo(datos.revisionId()))
            .isEqualTo("VENCIDA");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("PENDIENTE");
        assertThat(contarTareasRevisionRiesgo(datos.revisionId()))
            .isEqualTo(1);
        assertThat(contarSuscripcionesRiesgo(datos.procesoId())).isZero();
        assertThat(rolesTareasMotor(datos.procesoId()))
            .contains("tareaRevisionRiesgoManual");
        assertThat(tiposEventoDeRevision(datos.revisionId()))
            .contains(
                "EVALUACION_EXTERNA_VENCIDA",
                "TAREA_REVISION_RIESGO_CREADA"
            );
        assertThat(contarDecisionesDeRevision(datos.revisionId()))
            .isEqualTo(3);
    }

    @Test
    @DisplayName("El analista resuelve, consolida el máximo y recalcula el plan")
    void analistaResuelveYRecalculaPlan() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());

        JsonNode lista = listarRevisionesRiesgo("analista_riesgos");
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("id").asText()).isEqualTo(tarea);

        resolverRevisionRiesgo(
            tarea,
            "analista_riesgos",
            "ALTO",
            "El proveedor externo no respondió en el plazo",
            "Consulta y registro de incidente"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoTarea").value("RESUELTA"))
            .andExpect(jsonPath("$.estadoRevision").value("EJECUTANDO"))
            .andExpect(jsonPath("$.nivelRiesgo").value("ALTO"));

        assertThat(estadoRevision(datos.revisionId())).isEqualTo("EJECUTANDO");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("RESUELTA");
        assertThat(estadoEvaluacionManual(datos.revisionId()))
            .isEqualTo("MANUAL");
        assertThat(nivelEvaluacionManual(datos.revisionId())).isEqualTo("ALTO");
        assertThat(contarEvaluacionesManuales(datos.revisionId())).isEqualTo(1);
        assertThat(contarDecisionesDeRevision(datos.revisionId())).isEqualTo(3);
        assertThat(rolesTareasNegocioDeRevision(datos.revisionId()))
            .containsExactly("GERENTE", "RIESGOS", "SUPERVISOR");
        assertThat(contarEventosTipoDeRevision(
            datos.revisionId(), "REVISION_RIESGO_RESUELTA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            datos.revisionId(), "PLAN_APROBACION_RECALCULADO")).isEqualTo(1);
        assertThat(procesoActivo(datos.procesoId())).isTrue();
        assertThat(contarSuscripcionesEjecucion(datos.procesoId())).isEqualTo(1);
        assertThat(estadoIntento(datos.revisionId())).isEqualTo("PENDIENTE");

        ejecutarTrabajador();
        assertThat(estadoRevision(datos.revisionId())).isEqualTo("EJECUTADA");
    }

    @Test
    @DisplayName("El jefe de Riesgos también puede resolver")
    void jefeRiesgosResuelve() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        resolverRevisionRiesgo(
            idTareaRevisionRiesgo(datos.revisionId()),
            "jefe_riesgos",
            "ALTO",
            "Revisión del jefe de Riesgos",
            "Evidencia del jefe de Riesgos"
        ).andExpect(status().isOk());

        assertThat(estadoRevision(datos.revisionId())).isEqualTo("EJECUTANDO");
        assertThat(estadoEvaluacionManual(datos.revisionId()))
            .isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Un rol sin permiso no puede resolver")
    void rolSinPermisoNoResuelve() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());

        resolverRevisionRiesgo(
            tarea, "riesgos", "ALTO", "Motivo", "Evidencia")
            .andExpect(status().isForbidden());
        resolverRevisionRiesgo(
            tarea, "demo", "ALTO", "Motivo", "Evidencia")
            .andExpect(status().isForbidden());

        assertThat(estadoRevision(datos.revisionId()))
            .isEqualTo("REVISION_MANUAL");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El creador con rol de revisor no resuelve su propia revisión")
    void creadorConRolRevisorNoResuelve() throws Exception {
        otorgarRol("demo", "ANALISTA_RIESGOS");
        try {
            DatosRiesgo datos = crearConTimeout();
            resolverRevisionRiesgo(
                idTareaRevisionRiesgo(datos.revisionId()),
                "demo",
                "ALTO",
                "Intento del creador",
                "Evidencia del creador"
            ).andExpect(status().isForbidden());

            assertThat(estadoRevision(datos.revisionId()))
                .isEqualTo("REVISION_MANUAL");
            assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
                .isEqualTo("PENDIENTE");
        } finally {
            quitarRol("demo", "ANALISTA_RIESGOS");
        }
    }

    @Test
    @DisplayName("Una segunda resolución responde 409 y no aplica dos veces")
    void resolucionDuplicadaNoAplicaDosVeces() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());

        resolverRevisionRiesgo(
            tarea, "analista_riesgos", "ALTO", "Motivo", "Evidencia")
            .andExpect(status().isOk());
        resolverRevisionRiesgo(
            tarea, "analista_riesgos", "ALTO", "Motivo", "Evidencia")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo")
                .value("REVISION_RIESGO_YA_RESUELTA"));

        assertThat(contarEvaluacionesManuales(datos.revisionId())).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            datos.revisionId(), "REVISION_RIESGO_RESUELTA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos resoluciones simultáneas aplican una sola vez")
    void resolucionConcurrenteAplicaUnaSolaVez() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<Integer> primera = ejecutor.submit(() -> resolverConcurrente(
                tarea, listos, salida));
            Future<Integer> segunda = ejecutor.submit(() -> resolverConcurrente(
                tarea, listos, salida));
            assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
            salida.countDown();

            List<Integer> estados = List.of(
                primera.get(20, TimeUnit.SECONDS),
                segunda.get(20, TimeUnit.SECONDS)
            );
            assertThat(estados).containsExactlyInAnyOrder(200, 409);
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(contarEvaluacionesManuales(datos.revisionId())).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            datos.revisionId(), "REVISION_RIESGO_RESUELTA")).isEqualTo(1);
        assertThat(estadoRevision(datos.revisionId())).isEqualTo("EJECUTANDO");
    }

    @Test
    @DisplayName("Un nivel manual inferior al interno se rechaza")
    void nivelInferiorAlInternoSeRechaza() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        resolverRevisionRiesgo(
            idTareaRevisionRiesgo(datos.revisionId()),
            "analista_riesgos",
            "MEDIO",
            "Intento de bajar el riesgo",
            "Evidencia insuficiente"
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(estadoRevision(datos.revisionId()))
            .isEqualTo("REVISION_MANUAL");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("PENDIENTE");
        assertThat(contarEvaluacionesManuales(datos.revisionId())).isZero();
    }

    @Test
    @DisplayName("La resolución exige motivo y evidencia")
    void resolucionExigeMotivoYEvidencia() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());

        resolverRevisionRiesgo(tarea, "analista_riesgos", "ALTO", "", "Evidencia")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        resolverRevisionRiesgo(tarea, "analista_riesgos", "ALTO", "Motivo", "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El evento antes del vencimiento gana y cancela el timer")
    void eventoAntesDelVencimientoGana() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        aprobarTodas(solicitudId);

        notificarRiesgo(
            solicitudId, 1, "ALTO", evento(),
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PROCESADO"));

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(buscarTimer(procesoId, "vencerRiesgoExterno")).isNull();
        assertThat(contarTareasRevisionRiesgo(revisionId)).isZero();
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();
    }

    @Test
    @DisplayName("El evento durante la revisión manual queda obsoleto")
    void eventoDuranteRevisionManualEsObsoleto() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String evento = evento();

        notificarRiesgo(
            datos.solicitudId(), 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("OBSOLETO"));

        assertThat(estadoEventoExterno(evento)).isEqualTo("OBSOLETO");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El evento después de la resolución manual queda obsoleto")
    void eventoDespuesDeResolucionManualEsObsoleto() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        resolverRevisionRiesgo(
            idTareaRevisionRiesgo(datos.revisionId()),
            "analista_riesgos",
            "ALTO",
            "Revisión manual",
            "Evidencia manual"
        ).andExpect(status().isOk());

        String evento = evento();
        notificarRiesgo(
            datos.solicitudId(), 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("OBSOLETO"));

        assertThat(estadoEventoExterno(evento)).isEqualTo("OBSOLETO");
        assertThat(contarEvaluacionesManuales(datos.revisionId())).isEqualTo(1);
        assertThat(estadoRevision(datos.revisionId())).isEqualTo("EJECUTANDO");
    }

    @Test
    @DisplayName("La cancelación con tarea manual no reactiva la revisión")
    void cancelacionConTareaManual() throws Exception {
        DatosRiesgo datos = crearConTimeout();
        String tarea = idTareaRevisionRiesgo(datos.revisionId());

        cancelar(datos.solicitudId(), "demo", "Cancelación manual")
            .andExpect(status().isOk());

        assertThat(estadoRevision(datos.revisionId())).isEqualTo("CANCELADA");
        assertThat(estadoTareaRevisionRiesgo(datos.revisionId()))
            .isEqualTo("CANCELADA");
        assertThat(procesoFinalizado(datos.procesoId())).isTrue();

        resolverRevisionRiesgo(
            tarea, "analista_riesgos", "ALTO", "Motivo", "Evidencia")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo")
                .value("REVISION_RIESGO_YA_RESUELTA"));

        String evento = evento();
        notificarRiesgo(
            datos.solicitudId(), 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("OBSOLETO"));
        assertThat(estadoEventoExterno(evento)).isEqualTo("OBSOLETO");
    }

    @Test
    @DisplayName("Los reintentos agotados crean un incidente de riesgo externo")
    void reintentosAgotadosCreanIncidente() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        proveedorRiesgo.fallarProxima();
        ejecutarRiesgoExterno();
        ejecutarRiesgoExterno();
        ejecutarRiesgoExterno();

        assertThat(intentosEvaluacionRiesgo(revisionId)).isEqualTo(3);
        assertThat(estadoIncidente(revisionId, "RIESGO_EXTERNO"))
            .isEqualTo("ABIERTO");
        assertThat(incidenteReintentable(revisionId, "RIESGO_EXTERNO"))
            .isFalse();

        proveedorRiesgo.dejarDeFallar();
        aprobarTodas(solicitudId);
        ejecutarTimer(procesoId, "vencerRiesgoExterno");
        resolverRevisionRiesgo(
            idTareaRevisionRiesgo(revisionId),
            "analista_riesgos",
            "ALTO",
            "Revisión manual tras fallo de envío",
            "Incidente y consulta"
        ).andExpect(status().isOk());

        assertThat(estadoIncidente(revisionId, "RIESGO_EXTERNO"))
            .isEqualTo("RESUELTO");
    }

    private int resolverConcurrente(
        String tarea,
        CountDownLatch listos,
        CountDownLatch salida
    ) throws Exception {
        listos.countDown();
        if (!salida.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "No se liberó la barrera de resolución");
        }
        return resolverRevisionRiesgo(
            tarea,
            "analista_riesgos",
            "ALTO",
            "Resolución concurrente",
            "Evidencia concurrente"
        ).andReturn().getResponse().getStatus();
    }

    private DatosRiesgo crearConTimeout() throws Exception {        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobarTodas(solicitudId);
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);
        ejecutarTimer(procesoId, "vencerRiesgoExterno");

        return new DatosRiesgo(solicitudId, revisionId, procesoId);
    }

    private void aprobarTodas(String solicitudId) throws Exception {
        aprobar(
            idTareaAprobacion(solicitudId, "SUPERVISOR"),
            "supervisor",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitudId, "GERENTE"),
            "gerente",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitudId, "RIESGOS"),
            "riesgos",
            null
        ).andExpect(status().isOk());
    }

    private String evento() {
        return "evt-" + UUID.randomUUID();
    }

    private record DatosRiesgo(
        String solicitudId,
        String revisionId,
        String procesoId
    ) {
    }
}
