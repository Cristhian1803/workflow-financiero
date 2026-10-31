package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.puerto.PuertoCancelacionProceso;
import com.workflow.financiero.aplicacion.servicio.ResultadoCancelacionSolicitud;
import com.workflow.financiero.aplicacion.servicio.ResultadoDecisionAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioCancelacionSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioDecisionAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioInicioProceso;
import com.workflow.financiero.aplicacion.servicio.ServicioNotificacionEscalamiento;
import com.workflow.financiero.aplicacion.servicio.ServicioReasignacionTarea;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.excepcion.TareaYaResueltaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Pruebas del control local de cancelación frente a aprobación, timers y
 * duplicados. La cancelación real del proceso se sustituye por un adaptador de
 * prueba que registra las solicitudes.
 */
@Import(PruebaConcurrenciaCancelacionTest.ConfiguracionCancelacion.class)
class PruebaConcurrenciaCancelacionTest extends PruebaProcesoAprobacionBase {

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionCancelacion {

        @Bean
        @Primary
        AdaptadorCancelacionPrueba adaptadorCancelacionPrueba() {
            return new AdaptadorCancelacionPrueba();
        }
    }

    @Autowired
    ServicioCancelacionSolicitud servicioCancelacion;

    @Autowired
    ServicioUsuarioActual servicioUsuarioActual;

    @Autowired
    ServicioDecisionAprobacion servicioDecision;

    @Autowired
    ServicioNotificacionEscalamiento servicioNotificacion;

    @Autowired
    ServicioReasignacionTarea servicioReasignacion;

    @Autowired
    AdaptadorCancelacionPrueba adaptadorCancelacion;

    @Autowired
    SolicitudFinancieraRepositorio solicitudRepositorio;

    @Autowired
    RevisionSolicitudRepositorio revisionRepositorio;

    @Autowired
    TipoOperacionRepositorio tipoOperacionRepositorio;

    @Autowired
    MonedaRepositorio monedaRepositorio;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    ServicioTransicionRevision servicioTransicion;

    @Autowired
    ServicioInicioProceso servicioInicioProceso;

    @BeforeEach
    void limpiarAdaptador() {
        adaptadorCancelacion.limpiar();
    }

    @Test
    @DisplayName("Cancelar antes de aprobar impide una aprobación posterior")
    void cancelarAntesDeAprobarImpideAprobacion() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        ResultadoCancelacionSolicitud resultado = cancelar(
            solicitudId, revisionId, "demo", "El solicitante desiste");

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.CANCELADA);
        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaId)).isEqualTo("CANCELADA");
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
        assertThat(adaptadorCancelacion.llamadas().getFirst().idProceso())
            .isEqualTo(procesoId);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
        assertThat(actorDeEvento(revisionId, "CANCELACION_SOLICITADA"))
            .isEqualTo(idUsuario("demo"));

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TAREA_YA_RESUELTA"));

        assertThat(contarDecisionesDeRevision(revisionId)).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "APROBACION_REGISTRADA")).isZero();
        assertThat(observaciones(revisionId)).isEmpty();
        // La cancelación real del proceso se conecta en una iteración posterior.
        assertThat(procesoActivo(procesoId)).isTrue();
    }

    @Test
    @DisplayName("Una aprobación parcial puede completarse con una cancelación")
    void aprobacionParcialLuegoCancelacion() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaSupervisor = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String tareaGerente = idTareaAprobacion(solicitudId, "GERENTE");

        aprobar(tareaSupervisor, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaGerente)).isEqualTo("PENDIENTE");

        ResultadoCancelacionSolicitud resultado = cancelar(
            solicitudId, revisionId, "demo", "Ya no se requiere la operación");

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.CANCELADA);
        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaGerente)).isEqualTo("CANCELADA");
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_CANCELADA")).isEqualTo(1);
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
        assertThat(procesoActivo(procesoId)).isTrue();
    }

    @Test
    @DisplayName("Cancelar una revisión aprobada controlada no la deja ejecutada")
    void cancelarRevisionAprobadaControlada() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");

        aprobar(tareaId, "supervisor", null)
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("APROBADA");
        // La ejecución controlada actual no representa un efecto financiero.
        assertThat(observaciones(revisionId)).isNotEmpty();

        ResultadoCancelacionSolicitud resultado = cancelar(
            solicitudId, revisionId, "demo", "Se cancela antes de ejecutar");

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.CANCELADA);
        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
    }

    @Test
    @DisplayName("Una cancelación duplicada no repite la transición ni el evento")
    void cancelacionDuplicadaNoRepiteEventos() {
        JsonNode creada = crearSinExcepcion("demo", 25_000_000);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        cancelar(solicitudId, revisionId, "demo", "Primera cancelación");

        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "demo", "Segunda cancelación"))
            .isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
    }

    @Test
    @DisplayName("Una revisión que no es la actual no se cancela")
    void revisionNoActualNoSeCancela() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String tareaId = idTareaAprobacionDeRevision(revisionId, "SUPERVISOR");

        String segundaRevision = crearRevisionAdicional(solicitudId, 25_000_000);

        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "demo", "No corresponde"))
            .isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadoTarea(tareaId)).isEqualTo("PENDIENTE");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isZero();
        assertThat(adaptadorCancelacion.llamadas()).isEmpty();
        assertThat(estadoRevision(segundaRevision))
            .isEqualTo("PENDIENTE_APROBACION");
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

        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "demo", "No corresponde"))
            .isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isZero();
        assertThat(adaptadorCancelacion.llamadas()).isEmpty();
    }

    @Test
    @DisplayName("Solo el dueño o un administrador pueden cancelar")
    void soloPropietarioOAdministradorPuedenCancelar() {
        JsonNode creada = crearSinExcepcion("demo", 25_000_000);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "demo2", "No es el dueño"))
            .isInstanceOf(SolicitudNoEncontradaException.class);
        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "supervisor", "No es el dueño"))
            .isInstanceOf(AccesoDenegadoException.class);

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(adaptadorCancelacion.llamadas()).isEmpty();

        cancelar(solicitudId, revisionId, "admin", "Cancelación administrativa");

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
    }

    @Test
    @DisplayName("Los timers no generan efectos después de cancelar")
    void timersNoGeneranEfectosDespuesDeCancelar() {
        JsonNode creada = crearSinExcepcion("demo", 25_000_000);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaId = idTareaAprobacion(solicitudId, "SUPERVISOR");
        String idMotor = identificadorTareaMotor(revisionId, "SUPERVISOR");

        cancelar(solicitudId, revisionId, "demo", "Se cancela la operación");

        assertThat(buscarTimer(procesoId, "notificarSupervisor24h")).isNotNull();
        assertThat(buscarTimer(procesoId, "reasignarSupervisor48h")).isNotNull();

        ejecutarTimer(procesoId, "notificarSupervisor24h");
        ejecutarTimer(procesoId, "reasignarSupervisor48h");

        assertThat(servicioNotificacion.notificarEscalamiento(
            idMotor, "corr-tardia")).isFalse();
        assertThat(servicioReasignacion.reasignar(
            idMotor, "corr-tardia")).isFalse();
        assertThat(contarNotificacionesDeRevisionTotal(revisionId)).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "NOTIFICACION_ESCALAMIENTO")).isZero();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "TAREA_REASIGNADA")).isZero();
        assertThat(estadoTarea(tareaId)).isEqualTo("CANCELADA");
        assertThat(asignadoTarea(tareaId)).isNull();
    }

    @Test
    @DisplayName("Se puede cancelar una revisión recibida sin proceso")
    void cancelarRevisionRecibidaSinProceso() {
        Usuario usuario = usuarioRepositorio.findByUsuario("demo")
            .orElseThrow();
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL")
            .orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        SolicitudFinanciera solicitud = solicitudRepositorio.save(
            new SolicitudFinanciera(usuario));
        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                1,
                tipo,
                BigDecimal.valueOf(25_000_000),
                moneda,
                "Pago de prueba",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);

        ResultadoCancelacionSolicitud resultado = cancelar(
            solicitud.getId().toString(),
            revision.getId().toString(),
            "demo",
            "Cancelación temprana"
        );

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.CANCELADA);
        assertThat(estadoRevision(revision.getId().toString()))
            .isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revision.getId().toString(), "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(adaptadorCancelacion.llamadas()).isEmpty();
    }

    @Test
    @DisplayName("El motivo de cancelación es obligatorio")
    void motivoDeCancelacionObligatorio() {
        JsonNode creada = crearSinExcepcion("demo", 25_000_000);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        assertThatThrownBy(() -> cancelar(
            solicitudId, revisionId, "demo", " "))
            .isInstanceOf(DecisionInvalidaException.class);

        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isZero();
        assertThat(adaptadorCancelacion.llamadas()).isEmpty();
    }

    @RepeatedTest(3)
    @DisplayName("Aprobación y cancelación concurrentes dejan un solo resultado vigente")
    void aprobacionYCancelacionConcurrentes() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaSupervisor = idTareaAprobacion(solicitudId, "SUPERVISOR");

        UsuarioActual supervisor = servicioUsuarioActual.obtenerActual("supervisor");
        UsuarioActual demo = servicioUsuarioActual.obtenerActual("demo");
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        AtomicReference<ResultadoDecisionAprobacion> aprobacion =
            new AtomicReference<>();
        AtomicReference<ResultadoCancelacionSolicitud> cancelacion =
            new AtomicReference<>();
        AtomicReference<Throwable> errorAprobacion = new AtomicReference<>();
        AtomicReference<Throwable> errorCancelacion = new AtomicReference<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            ejecutor.submit(() -> {
                try {
                    inicio.await();
                    aprobacion.set(servicioDecision.aprobar(
                        UUID.fromString(tareaSupervisor),
                        supervisor,
                        null,
                        "corr-concurrente"
                    ));
                } catch (Throwable error) {
                    errorAprobacion.set(error);
                } finally {
                    fin.countDown();
                }
            });
            ejecutor.submit(() -> {
                try {
                    inicio.await();
                    cancelacion.set(servicioCancelacion.cancelar(
                        UUID.fromString(solicitudId),
                        UUID.fromString(revisionId),
                        demo,
                        "Cancelación concurrente",
                        "corr-concurrente"
                    ));
                } catch (Throwable error) {
                    errorCancelacion.set(error);
                } finally {
                    fin.countDown();
                }
            });
            inicio.countDown();
            assertThat(fin.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            ejecutor.shutdownNow();
        }

        assertThat(errorCancelacion.get()).isNull();
        assertThat(cancelacion.get()).isNotNull();
        if (errorAprobacion.get() != null) {
            assertThat(errorAprobacion.get())
                .isInstanceOf(TareaYaResueltaException.class);
        }

        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
        assertThat(estadosTareasDeRevision(revisionId).stream()
            .filter(estado -> estado.endsWith(":PENDIENTE"))
            .count()).isZero();
        assertThat(contarDecisionesDeRevision(revisionId)).isBetween(0L, 1L);
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(procesoActivo(procesoId)).isTrue();
    }

    @RepeatedTest(3)
    @DisplayName("Dos cancelaciones concurrentes producen un solo efecto")
    void cancelacionesConcurrentesProducenUnSoloEfecto() throws Exception {
        JsonNode creada = crearSinExcepcion("demo", 25_000_000);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();

        UsuarioActual demo = servicioUsuarioActual.obtenerActual("demo");
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(2);
        ConcurrentLinkedQueue<ResultadoCancelacionSolicitud> exitos =
            new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errores = new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        exitos.add(servicioCancelacion.cancelar(
                            UUID.fromString(solicitudId),
                            UUID.fromString(revisionId),
                            demo,
                            "Cancelación concurrente",
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
        assertThat(errores.peek())
            .isInstanceOf(TransicionEstadoInvalidaException.class);
        assertThat(estadoRevision(revisionId)).isEqualTo("CANCELADA");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "CANCELACION_SOLICITADA")).isEqualTo(1);
        assertThat(adaptadorCancelacion.llamadas()).hasSize(1);
    }

    private UUID actorDeEvento(String revisionId, String tipo) {
        return jdbcTemplate.queryForObject(
            "SELECT evensoli_actor_usua_id FROM evento_solicitud "
                + "WHERE evensoli_revisoli_id = ? AND evensoli_tipo = ?",
            UUID.class,
            UUID.fromString(revisionId),
            tipo);
    }

    private ResultadoCancelacionSolicitud cancelar(
        String solicitudId,
        String revisionId,
        String usuario,
        String motivo
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(usuario);
        return servicioCancelacion.cancelar(
            UUID.fromString(solicitudId),
            UUID.fromString(revisionId),
            actual,
            motivo,
            "corr-cancelacion"
        );
    }

    private JsonNode crearSinExcepcion(String usuario, long monto) {
        try {
            return crear(usuario, monto, "NORMAL");
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo crear la solicitud", error);
        }
    }

    private String crearRevisionAdicional(String solicitudId, long monto) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(UUID.fromString(solicitudId))
            .orElseThrow();
        RevisionSolicitud actual = solicitud.getRevisionActual();
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL")
            .orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                actual.getNumero() + 1,
                tipo,
                BigDecimal.valueOf(monto),
                moneda,
                "Pago corregido",
                "Se ajusta el monto a la documentación"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-revision", "Validación")
        );

        RevisionSolicitud enValidacion = revisionRepositorio
            .buscarDetallePorId(revision.getId())
            .orElseThrow();
        enValidacion.registrarNivelRiesgo(NivelRiesgo.BAJO);
        revisionRepositorio.saveAndFlush(enValidacion);

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(null, null, "corr-revision", "Riesgo")
        );
        servicioInicioProceso.iniciar(
            solicitud.getId(),
            revision.getId(),
            "corr-revision"
        );

        return revision.getId().toString();
    }

    static class AdaptadorCancelacionPrueba implements PuertoCancelacionProceso {

        private final List<LlamadaCancelacion> llamadas =
            new CopyOnWriteArrayList<>();

        @Override
        public void solicitarCancelacion(String idProceso, String motivo) {
            llamadas.add(new LlamadaCancelacion(idProceso, motivo));
        }

        List<LlamadaCancelacion> llamadas() {
            return List.copyOf(llamadas);
        }

        void limpiar() {
            llamadas.clear();
        }
    }

    record LlamadaCancelacion(String idProceso, String motivo) {
    }
}
