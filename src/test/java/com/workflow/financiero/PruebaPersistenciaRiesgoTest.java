package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Comprueba que un evento temprano sobrevive a un reinicio del contexto y que
 * el trabajador lo aplica una sola vez.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PruebaPersistenciaRiesgoTest extends PruebaProcesoAprobacionBase {

    private static String solicitudId;
    private static String revisionId;
    private static String procesoId;
    private static String evento;

    @Override
    @BeforeEach
    void prepararBase() {
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @Override
    @AfterEach
    void limpiarBase() {
        // La limpieza se hace al final del segundo método para no borrar el
        // inbox que debe sobrevivir al reinicio.
    }

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("El evento temprano queda guardado antes del reinicio")
    void eventoTempranoQuedaGuardadoAntesDelReinicio() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        solicitudId = creada.get("id").asText();
        revisionId = creada.get("idRevision").asText();
        procesoId = identificadorProceso(solicitudId);
        evento = "evt-" + UUID.randomUUID();

        notificarRiesgo(
            solicitudId, 1, "ALTO", evento,
            Instant.now(), Instant.now().toString(), null)
            .andExpect(status().isAccepted());

        assertThat(estadoEventoExterno(evento)).isEqualTo("PENDIENTE");
        assertThat(estadoRevision(revisionId))
            .isEqualTo("PENDIENTE_APROBACION");
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Tras el reinicio el evento se aplica una sola vez")
    void trasElReinicioSeAplicaUnaSolaVez() throws Exception {
        aprobarTodas(solicitudId);
        assertThat(contarSuscripcionesRiesgo(procesoId)).isEqualTo(1);

        ejecutarRiesgoExterno();

        assertThat(estadoEventoExterno(evento)).isEqualTo("PROCESADO");
        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);

        ejecutarRiesgoExterno();
        assertThat(contarEventosTipoDeRevision(
            revisionId, "EVALUACION_EXTERNA_RESUELTA")).isEqualTo(1);

        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE operacion_simulada");
        jdbcTemplate.execute(
            "TRUNCATE TABLE evento_externo, evaluacion_riesgo");
        proveedorDirecto.reiniciar();
        proveedorRiesgo.reiniciar();
    }

    private void aprobarTodas(String solicitud) throws Exception {
        aprobar(
            idTareaAprobacion(solicitud, "SUPERVISOR"),
            "supervisor",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitud, "GERENTE"),
            "gerente",
            null
        ).andExpect(status().isOk());
        aprobar(
            idTareaAprobacion(solicitud, "RIESGOS"),
            "riesgos",
            null
        ).andExpect(status().isOk());
    }
}
