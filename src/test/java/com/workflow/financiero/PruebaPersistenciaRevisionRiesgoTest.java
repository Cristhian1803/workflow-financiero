package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Comprueba que la tarea manual sobrevive a un reinicio del contexto y que
 * puede resolverse después.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PruebaPersistenciaRevisionRiesgoTest
    extends PruebaProcesoAprobacionBase {

    private static String solicitudId;
    private static String revisionId;
    private static String procesoId;

    @Override
    @BeforeEach
    void prepararBase() {
        sembrador.sembrar();
        activarUsuariosDemo();
    }

    @Override
    @AfterEach
    void limpiarBase() {
        // La limpieza se hace al final del segundo método para no borrar la
        // tarea que debe sobrevivir al reinicio.
    }

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("La tarea manual queda persistida antes del reinicio")
    void tareaManualQuedaPersistidaAntesDelReinicio() throws Exception {
        JsonNode creada = crear("demo", 250_000_000, "NORMAL");
        solicitudId = creada.get("id").asText();
        revisionId = creada.get("idRevision").asText();
        procesoId = identificadorProceso(solicitudId);

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

        ejecutarTimer(procesoId, "vencerRiesgoExterno");

        assertThat(estadoRevision(revisionId)).isEqualTo("REVISION_MANUAL");
        assertThat(estadoTareaRevisionRiesgo(revisionId))
            .isEqualTo("PENDIENTE");
        assertThat(idTareaRevisionRiesgo(revisionId)).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Tras el reinicio la tarea se puede resolver")
    void trasElReinicioSePuedeResolver() throws Exception {
        String tarea = idTareaRevisionRiesgo(revisionId);
        assertThat(tarea).isNotBlank();

        JsonNode lista = listarRevisionesRiesgo("analista_riesgos");
        assertThat(lista).hasSize(1);

        resolverRevisionRiesgo(
            tarea,
            "analista_riesgos",
            "ALTO",
            "Revisión tras reinicio",
            "Evidencia persistida"
        )
            .andExpect(status().isOk());

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(estadoTareaRevisionRiesgo(revisionId))
            .isEqualTo("RESUELTA");
        assertThat(estadoEvaluacionManual(revisionId)).isEqualTo("MANUAL");

        limpiarProcesos();
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE operacion_simulada");
        jdbcTemplate.execute(
            "TRUNCATE TABLE evento_externo, evaluacion_riesgo, tarea_revision_riesgo");
        proveedorDirecto.reiniciar();
        proveedorRiesgo.reiniciar();
    }
}
