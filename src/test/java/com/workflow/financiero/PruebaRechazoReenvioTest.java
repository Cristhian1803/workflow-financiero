package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.aplicacion.servicio.CrearSolicitudComando;
import com.workflow.financiero.aplicacion.servicio.ServicioReenvioSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.SolicitudDetalle;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.infraestructura.ejecucion.AdaptadorEjecucionControlada.TipoObservacion;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

/**
 * Cubre el rechazo desde todas las ramas, el reenvío con revisión nueva y las
 * reglas de plazo, límite y concurrencia.
 */
@Import(PruebaRechazoReenvioTest.ConfiguracionReloj.class)
class PruebaRechazoReenvioTest extends PruebaProcesoAprobacionBase {

    @TestConfiguration
    static class ConfiguracionReloj {

        @Bean
        @Primary
        RelojMutable relojPrueba() {
            return new RelojMutable(Instant.now(), ZoneOffset.UTC);
        }
    }

    @Autowired
    RelojMutable reloj;

    @Autowired
    RevisionSolicitudRepositorio revisionRepositorio;

    @Autowired
    ServicioReenvioSolicitud servicioReenvio;

    @Autowired
    ServicioUsuarioActual servicioUsuarioActual;

    @BeforeEach
    void reiniciarReloj() {
        reloj.fijar(Instant.now());
    }

    @Test
    @DisplayName("Rechazo de Supervisor cancela a Gerente")
    void rechazoSupervisorCancelaGerente() throws Exception {
        verificarRechazo(
            100_000_000, "NORMAL", "SUPERVISOR", "supervisor", "GERENTE");
    }

    @Test
    @DisplayName("Rechazo de Gerente cancela a Supervisor")
    void rechazoGerenteCancelaSupervisor() throws Exception {
        verificarRechazo(
            100_000_000, "NORMAL", "GERENTE", "gerente", "SUPERVISOR");
    }

    @Test
    @DisplayName("Rechazo de Riesgos cancela las otras dos ramas")
    void rechazoRiesgosCancelaLasOtrasRamas() throws Exception {
        verificarRechazo(
            250_000_000, "NORMAL", "RIESGOS", "riesgos",
            "SUPERVISOR", "GERENTE");
    }

    @Test
    @DisplayName("Rechazo de Supervisor cancela Gerente y Riesgos")
    void rechazoSupervisorCancelaGerenteYRiesgos() throws Exception {
        verificarRechazo(
            250_000_000, "NORMAL", "SUPERVISOR", "supervisor",
            "GERENTE", "RIESGOS");
    }

    @Test
    @DisplayName("Rechazo de Gerente cancela Supervisor y Riesgos")
    void rechazoGerenteCancelaSupervisorYRiesgos() throws Exception {
        verificarRechazo(
            250_000_000, "NORMAL", "GERENTE", "gerente",
            "SUPERVISOR", "RIESGOS");
    }

    @Test
    @DisplayName("Una aprobación previa no se marca como cancelada")
    void aprobacionPreviaNoSeCancela() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaGerente = idTareaAprobacionDeRevision(revisionId, "GERENTE");
        String tareaSupervisor = idTareaAprobacionDeRevision(
            revisionId, "SUPERVISOR");

        aprobar(tareaGerente, "gerente", null)
            .andExpect(status().isOk());
        rechazar(tareaSupervisor, "supervisor", "No corresponde")
            .andExpect(status().isOk());

        assertThat(estadoTarea(tareaGerente)).isEqualTo("APROBADA");
        assertThat(estadoTarea(tareaSupervisor)).isEqualTo("RECHAZADA");
        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(2);
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(tiposEventoDeRevision(revisionId))
            .contains("APROBACION_REGISTRADA", "RECHAZO_REGISTRADO");
    }

    @Test
    @DisplayName("Rechazar sin motivo devuelve 400")
    void rechazoSinMotivoDevuelve400() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String tarea = idTareaAprobacionDeRevision(
            creada.get("idRevision").asText(), "SUPERVISOR");

        rechazar(tarea, "supervisor", "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(estadoTarea(tarea)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("Un reenvío válido crea revisión y proceso nuevos")
    void reenvioValidoCreaRevisionYProcesoNuevos() throws Exception {
        JsonNode creada = crear("demo", 100_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();
        String proceso1 = identificadorProceso(solicitudId);
        String tareaSupervisor1 = idTareaAprobacionDeRevision(
            revision1, "SUPERVISOR");
        String tareaGerente1 = idTareaAprobacionDeRevision(
            revision1, "GERENTE");

        aprobar(tareaSupervisor1, "supervisor", null)
            .andExpect(status().isOk());
        rechazar(tareaGerente1, "gerente", "No corresponde")
            .andExpect(status().isOk());

        List<String> eventos1 = tiposEventoDeRevision(revision1);
        List<String> tareas1 = estadosTareasDeRevision(revision1);
        long decisiones1 = contarDecisionesDeRevision(revision1);

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 100_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());

        String revision2 = respuesta.get("idRevision").asText();
        String proceso2 = identificadorProcesoDeRevision(revision2);

        assertThat(respuesta.get("revision").asInt()).isEqualTo(2);
        assertThat(respuesta.get("estado").asText())
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("MEDIO");
        assertThat(listaTexto(respuesta.get("aprobacionesRequeridas")))
            .containsExactly("GERENTE", "SUPERVISOR");
        assertThat(revision2).isNotEqualTo(revision1);
        assertThat(proceso2).isNotEqualTo(proceso1);
        assertThat(estadoRevision(revision1)).isEqualTo("RECHAZADA");
        assertThat(procesoFinalizado(proceso1)).isTrue();
        assertThat(tiposEventoDeRevision(revision1)).isEqualTo(eventos1);
        assertThat(estadosTareasDeRevision(revision1)).isEqualTo(tareas1);
        assertThat(contarDecisionesDeRevision(revision1))
            .isEqualTo(decisiones1);
        assertThat(rolesTareasNegocioDeRevision(revision2))
            .containsExactly("GERENTE", "SUPERVISOR");
        assertThat(rolesTareasMotor(proceso2))
            .containsExactlyInAnyOrder("tareaGerente", "tareaSupervisor");
        assertThat(procesoActivo(proceso2)).isTrue();
        assertThat(tiposEventoDeRevision(revision2))
            .contains("REVISION_REENVIADA");
        assertThat(contarRevisiones(solicitudId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un reenvío con otro monto recalcula riesgo y plan")
    void reenvioRecalculaMontoYTramo() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 75_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());

        String revision2 = respuesta.get("idRevision").asText();
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("MEDIO");
        assertThat(listaTexto(respuesta.get("aprobacionesRequeridas")))
            .containsExactly("GERENTE", "SUPERVISOR");
        assertThat(rolesTareasNegocioDeRevision(revision2))
            .containsExactly("GERENTE", "SUPERVISOR");
    }

    @Test
    @DisplayName("Un reenvío a tipo sensible recalcula el riesgo alto")
    void reenvioRecalculaRiesgoSensible() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 25_000_000, "SENSIBLE")
            .andExpect(status().isCreated())
            .andReturn());

        String revision2 = respuesta.get("idRevision").asText();
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("ALTO");
        assertThat(listaTexto(respuesta.get("aprobacionesRequeridas")))
            .containsExactly("RIESGOS", "SUPERVISOR");
        assertThat(rolesTareasNegocioDeRevision(revision2))
            .containsExactly("RIESGOS", "SUPERVISOR");
    }

    @Test
    @DisplayName("Un reenvío sin aprobaciones queda aprobado")
    void reenvioSinAprobacionesQuedaAprobada() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 5_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());

        String revision2 = respuesta.get("idRevision").asText();
        String proceso2 = identificadorProcesoDeRevision(revision2);

        assertThat(respuesta.get("estado").asText()).isEqualTo("APROBADA");
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("BAJO");
        assertThat(listaTexto(respuesta.get("aprobacionesRequeridas")))
            .isEmpty();
        assertThat(rolesTareasNegocioDeRevision(revision2)).isEmpty();
        assertThat(procesoFinalizado(proceso2)).isTrue();
        assertThat(observaciones(revision2))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
        assertThat(tiposEventoDeRevision(revision2))
            .contains("EJECUCION_PREPARADA", "EJECUCION_CONTROLADA");
    }

    @Test
    @DisplayName("Un reenvío sobre 200 millones espera el riesgo externo")
    void reenvioConRiesgoExternoEspera() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        JsonNode respuesta = leer(reenviar(
                solicitudId, "demo", 250_000_000, "NORMAL")
            .andExpect(status().isCreated())
            .andReturn());

        String revision2 = respuesta.get("idRevision").asText();
        String proceso2 = identificadorProcesoDeRevision(revision2);

        assertThat(respuesta.get("estado").asText())
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(respuesta.get("nivelRiesgo").asText()).isEqualTo("ALTO");
        assertThat(respuesta.get("requiereEvaluacionExterna").asBoolean())
            .isTrue();
        assertThat(listaTexto(respuesta.get("aprobacionesRequeridas")))
            .containsExactly("GERENTE", "RIESGOS", "SUPERVISOR");
        assertThat(procesoActivo(proceso2)).isTrue();
        assertThat(contarSuscripcionesRiesgo(proceso2)).isZero();
        assertThat(tiposEventoDeRevision(revision2))
            .contains("EVALUACION_EXTERNA_PENDIENTE");
    }

    @Test
    @DisplayName("Un reenvío fuera de los 30 días devuelve 422")
    void reenvioFueraDeLos30Dias() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        Instant rechazo = fechaActualizacionRevision(revision1);
        reloj.fijar(rechazo.plus(Duration.ofDays(31)));

        reenviar(solicitudId, "demo", 25_000_000, "NORMAL")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("REENVIO_NO_PERMITIDO"));

        assertThat(contarRevisiones(solicitudId)).isEqualTo(1);
        assertThat(estadoRevision(revision1)).isEqualTo("RECHAZADA");
    }

    @Test
    @DisplayName("Un reenvío en el día 30 exacto se permite")
    void reenvioEnElLimiteDe30Dias() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        Instant rechazo = fechaActualizacionRevision(revision1);
        reloj.fijar(rechazo.plus(Duration.ofDays(30)));

        reenviar(solicitudId, "demo", 25_000_000, "NORMAL")
            .andExpect(status().isCreated());

        assertThat(contarRevisiones(solicitudId)).isEqualTo(2);
    }

    @Test
    @DisplayName("La cuarta revisión no se permite")
    void cuartaRevisionNoPermitida() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision = creada.get("idRevision").asText();

        for (int numero = 1; numero <= 3; numero++) {
            if (numero > 1) {
                JsonNode respuesta = leer(reenviar(
                        solicitudId, "demo", 25_000_000, "NORMAL")
                    .andExpect(status().isCreated())
                    .andReturn());
                revision = respuesta.get("idRevision").asText();
            }

            rechazar(
                idTareaAprobacionDeRevision(revision, "SUPERVISOR"),
                "supervisor",
                "No corresponde"
            ).andExpect(status().isOk());
        }

        reenviar(solicitudId, "demo", 25_000_000, "NORMAL")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("REENVIO_NO_PERMITIDO"));

        assertThat(contarRevisiones(solicitudId)).isEqualTo(3);
    }

    @Test
    @DisplayName("No se reenvía una solicitud que no está rechazada")
    void reenvioNoRechazadoDevuelve409() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();

        reenviar(solicitudId, "demo", 25_000_000, "NORMAL")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CONFLICTO_DATOS"));

        assertThat(contarRevisiones(solicitudId)).isEqualTo(1);
        assertThat(estadoRevision(creada.get("idRevision").asText()))
            .isEqualTo("PENDIENTE_APROBACION");
    }

    @Test
    @DisplayName("Un reenvío con datos inválidos devuelve 400")
    void reenvioDatosInvalidosDevuelve400() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipoOperacion", "NORMAL");
        cuerpo.put("monto", 0);
        cuerpo.put("moneda", "PYG");
        cuerpo.put("descripcion", "Pago corregido");
        cuerpo.put("justificacion", "Se ajusta el monto a la documentación");

        mockMvc.perform(conToken(
                post(RUTA_SOLICITUDES + "/" + solicitudId + "/reenvios"),
                token("demo"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(cuerpo)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        assertThat(contarRevisiones(solicitudId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Otro solicitante no puede ver ni reenviar la solicitud")
    void otroSolicitanteNoPuedeReenviar() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        reenviar(solicitudId, "demo2", 25_000_000, "NORMAL")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Un administrador que no es dueño recibe 403")
    void administradorNoDuenioRecibe403() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
            "supervisor",
            "No corresponde"
        ).andExpect(status().isOk());

        reenviar(solicitudId, "admin", 25_000_000, "NORMAL")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("Dos reenvíos concurrentes crean una sola revisión")
    void reenvioConcurrenteCreaUnaSolaRevision() throws Exception {
        JsonNode creada = crear("demo", 25_000_000, "NORMAL");
        String solicitudId = creada.get("id").asText();
        String revision1 = creada.get("idRevision").asText();

        rechazar(
            idTareaAprobacionDeRevision(revision1, "SUPERVISOR"),
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
        ConcurrentLinkedQueue<ResultadoReenvio> resultados =
            new ConcurrentLinkedQueue<>();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                ejecutor.submit(() -> {
                    try {
                        inicio.await();
                        SolicitudDetalle detalle = servicioReenvio.reenviar(
                            UUID.fromString(solicitudId),
                            demo,
                            comando,
                            "corr-concurrente"
                        );
                        resultados.add(new ResultadoReenvio(detalle, null));
                    } catch (Throwable error) {
                        resultados.add(new ResultadoReenvio(null, error));
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

        assertThat(resultados).hasSize(2);
        assertThat(resultados.stream()
            .filter(resultado -> resultado.detalle() != null))
            .hasSize(1);
        assertThat(resultados.stream()
            .filter(resultado -> resultado.error() != null))
            .hasSize(1);

        Throwable error = resultados.stream()
            .filter(resultado -> resultado.error() != null)
            .findFirst()
            .orElseThrow()
            .error();
        assertThat(error).isInstanceOfAny(
            TransicionEstadoInvalidaException.class,
            DataIntegrityViolationException.class
        );

        assertThat(contarRevisiones(solicitudId)).isEqualTo(2);
        Integer numeroActual = jdbcTemplate.queryForObject(
            "SELECT r.revisoli_numero FROM solicitud_financiera s "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = s.solifina_revisoli_actual_id "
                + "WHERE s.solifina_id = ?",
            Integer.class,
            UUID.fromString(solicitudId)
        );
        assertThat(numeroActual).isEqualTo(2);
        assertThat(estadoRevision(revision1)).isEqualTo("RECHAZADA");
    }

    private JsonNode verificarRechazo(
        long monto,
        String tipoOperacion,
        String rolRechaza,
        String usuarioRechaza,
        String... rolesCancelados
    ) throws Exception {
        JsonNode creada = crear("demo", monto, tipoOperacion);
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);
        String tareaRechazada = idTareaAprobacionDeRevision(
            revisionId, rolRechaza);

        rechazar(tareaRechazada, usuarioRechaza, "No corresponde")
            .andExpect(status().isOk());

        assertThat(estadoTarea(tareaRechazada)).isEqualTo("RECHAZADA");
        for (String rol : rolesCancelados) {
            assertThat(estadoTarea(idTareaAprobacionDeRevision(revisionId, rol)))
                .as("estado de %s", rol)
                .isEqualTo("CANCELADA");
        }
        assertThat(estadoRevision(revisionId)).isEqualTo("RECHAZADA");
        assertThat(procesoActivo(procesoId)).isFalse();
        assertThat(procesoFinalizado(procesoId)).isTrue();
        assertThat(rolesTareasMotor(procesoId)).isEmpty();
        assertThat(observaciones(revisionId)).isEmpty();
        assertThat(tiposEventoDeRevision(revisionId))
            .contains("RECHAZO_REGISTRADO", "TAREA_CANCELADA")
            .doesNotContain(
                "EJECUCION_PREPARADA",
                "EJECUCION_CONTROLADA"
            );
        assertThat(contarDecisionesDeRevision(revisionId)).isEqualTo(1);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();

        return creada;
    }

    private Instant fechaActualizacionRevision(String revisionId) {
        RevisionSolicitud revision = revisionRepositorio
            .findById(UUID.fromString(revisionId))
            .orElseThrow();
        return revision.getActualizadoEn();
    }

    private List<String> listaTexto(JsonNode nodo) {
        List<String> valores = new ArrayList<>();
        nodo.forEach(valor -> valores.add(valor.asText()));
        return valores;
    }

    private record ResultadoReenvio(
        SolicitudDetalle detalle,
        Throwable error
    ) {
    }
}
