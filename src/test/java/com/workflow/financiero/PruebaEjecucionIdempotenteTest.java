package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Comprueba la intención durable, la frontera transaccional del trabajador y
 * el efecto único del proveedor simulado.
 */
class PruebaEjecucionIdempotenteTest extends PruebaProcesoAprobacionBase {

    @Autowired
    ServicioTransicionRevision servicioTransicion;

    @Test
    @DisplayName("Una revisión aprobada crea una sola intención")
    void revisionAprobadaCreaUnaSolaIntencion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(contarIntentos(revisionId)).isEqualTo(1);
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");
        assertThat(claveIntento(revisionId))
            .isEqualTo(solicitudId + ":1");
        assertThat(huellaIntento(revisionId)).matches("[0-9a-f]{64}");
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
        assertThat(proveedorDirecto.confirmaciones()).isZero();
    }

    @Test
    @DisplayName("El proveedor se invoca sin transacción y con la intención persistida")
    void proveedorSeInvocaSinTransaccion() throws Exception {
        crear("demo", 5_000_000, "NORMAL");

        ejecutarTrabajador();

        assertThat(proveedorDirecto.invocadoSinTransaccion()).isTrue();
        assertThat(proveedorDirecto.estadoIntentoEnPrimeraLlamada())
            .isEqualTo("ENVIANDO");
        assertThat(proveedorDirecto.reservas()).isEqualTo(1);
        assertThat(proveedorDirecto.confirmaciones()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una confirmación exitosa termina la revisión y el proceso")
    void confirmacionExitosaTerminaEjecucion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        ejecutarTrabajador();

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTADA");
        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(referenciaConfirmacion(revisionId)).startsWith("CONF-");
        assertThat(contarSuscripcionesEjecucion(procesoId)).isZero();
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_INICIADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_CONFIRMADA")).isEqualTo(1);
        assertThat(contarOperaciones("RESERVA")).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
        assertThat(estadoOperacion("reserva:" + solicitudId + ":" + revisionId))
            .isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("El proceso espera el resultado antes de confirmar")
    void procesoEsperaResultadoAntesDeConfirmar() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
        assertThat(contarOperaciones("RESERVA")).isZero();
        assertThat(contarOperaciones("CONFIRMACION")).isZero();
    }

    @RepeatedTest(3)
    @DisplayName("Dos preparaciones concurrentes no duplican la intención")
    void dosPreparacionesConcurrentesNoDuplicanIntencion() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        ConcurrentLinkedQueue<UUID> ids = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        IntentoEjecucion intento = servicioIntencion.preparar(
                            UUID.fromString(solicitudId),
                            UUID.fromString(revisionId),
                            "corr-preparar"
                        );
                        ids.add(intento.getId());
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
        assertThat(ids).hasSize(2).containsOnly(ids.peek());
        assertThat(contarIntentos(revisionId)).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EJECUCION_INICIADA")).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Dos trabajadores concurrentes producen una sola confirmación")
    void dosTrabajadoresConcurrentesProducenUnSoloEfecto() throws Exception {
        crear("demo", 5_000_000, "NORMAL");

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
                        procesadas.add(trabajadorEjecucion.ejecutarPendientes());
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
        assertThat(proveedorDirecto.confirmaciones()).isEqualTo(1);
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
    }

    @Test
    @DisplayName("Volver a ejecutar el trabajador no repite el efecto")
    void trabajadorRepetidoNoDuplicaEfecto() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();

        assertThat(ejecutarTrabajador()).isEqualTo(1);
        assertThat(ejecutarTrabajador()).isZero();

        assertThat(estadoIntento(revisionId)).isEqualTo("CONFIRMADA");
        assertThat(contarOperaciones("CONFIRMACION")).isEqualTo(1);
        assertThat(proveedorDirecto.confirmaciones()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una revisión no aprobada no crea intención")
    void revisionNoAprobadaNoCreaIntencion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        assertThatThrownBy(() -> servicioIntencion.preparar(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            "corr-no-aprobada"
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(contarIntentos(revisionId)).isZero();
    }

    @Test
    @DisplayName("Una revisión rechazada no crea intención")
    void revisionRechazadaNoCreaIntencion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacion(solicitudId, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(contarIntentos(revisionId)).isZero();
    }

    @Test
    @DisplayName("Una revisión ya cancelada no crea intención")
    void revisionCanceladaNoCreaIntencion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        servicioTransicion.transicionar(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            EstadoRevision.CANCELADA,
            new ContextoTransicion(
                null, null, "corr-cancelada", "Cancelación de prueba")
        );

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarIntentos(revisionId)).isZero();
        assertThatThrownBy(() -> servicioIntencion.preparar(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            "corr-cancelada"
        )).isInstanceOf(TransicionEstadoInvalidaException.class);
        assertThat(contarIntentos(revisionId)).isZero();
    }

    @Test
    @DisplayName("La restricción única por revisión impide una segunda intención")
    void unicidadPorRevisionImpideDuplicados() throws Exception {
        JsonNode creada = crear("demo", 5_000_000, "NORMAL");
        String revisionId = creada.get("idRevision").asText();

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO intento_ejecucion ("
                + "inteejec_id, inteejec_revisoli_id, inteejec_clave, "
                + "inteejec_huella, inteejec_estado, inteejec_intentos, "
                + "inteejec_version, inteejec_creado_en) "
                + "VALUES (?, ?, ?, ?, 'PENDIENTE', 0, 0, ?)",
            UUID.randomUUID(),
            UUID.fromString(revisionId),
            "otra-clave",
            "huella-distinta",
            Timestamp.from(Instant.now())
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contarIntentos(revisionId)).isEqualTo(1);
    }

    private long contarOperaciones(String accion) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operacion_simulada "
                + "WHERE opersimu_accion = ?",
            Long.class,
            accion);
    }

    private String estadoOperacion(String clave) {
        return jdbcTemplate.queryForObject(
            "SELECT opersimu_estado FROM operacion_simulada "
                + "WHERE opersimu_clave = ?",
            String.class,
            clave);
    }
}
