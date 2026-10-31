package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.infraestructura.ejecucion.AdaptadorEjecucionControlada.TipoObservacion;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que el proceso real active solo las ramas del plan para cada
 * combinación de monto y riesgo.
 */
class PruebaRutasProcesoTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("Cinco millones recorre la vía automática sin tareas")
    void cincoMillonesSinTareas() throws Exception {
        JsonNode creada = verificarRuta(5_000_000, "NORMAL");

        assertThat(observaciones(creada.get("idRevision").asText()))
            .extracting(observacion -> observacion.tipo())
            .containsExactlyInAnyOrder(
                TipoObservacion.PREPARACION,
                TipoObservacion.RESULTADO
            );
    }

    @Test
    @DisplayName("Diez millones sin riesgo alto no crea tareas")
    void diezMillonesSinTareas() throws Exception {
        verificarRuta(10_000_000, "NORMAL");
    }

    @Test
    @DisplayName("Diez millones con riesgo alto exige solo Riesgos")
    void diezMillonesAltoExigeRiesgos() throws Exception {
        JsonNode creada = verificarRuta(10_000_000, "SENSIBLE", "RIESGOS");
        String solicitudId = creada.get("id").asText();
        String revisionId = creada.get("idRevision").asText();
        String procesoId = identificadorProceso(solicitudId);

        aprobar(idTareaAprobacion(solicitudId, "RIESGOS"), "riesgos", null)
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
    @DisplayName("Diez millones un guaraní exige Supervisor")
    void diezMillonesUnGuaraniExigeSupervisor() throws Exception {
        verificarRuta(10_000_001, "NORMAL", "SUPERVISOR");
    }

    @Test
    @DisplayName("Veinticinco millones exige Supervisor")
    void veinticincoMillonesExigeSupervisor() throws Exception {
        verificarRuta(25_000_000, "NORMAL", "SUPERVISOR");
    }

    @Test
    @DisplayName("Cincuenta millones exige Supervisor")
    void cincuentaMillonesExigeSupervisor() throws Exception {
        verificarRuta(50_000_000, "NORMAL", "SUPERVISOR");
    }

    @Test
    @DisplayName("Cincuenta millones con riesgo alto exige Supervisor y Riesgos")
    void cincuentaMillonesAltoExigeSupervisorYRiesgos() throws Exception {
        verificarRuta(50_000_000, "SENSIBLE", "SUPERVISOR", "RIESGOS");
    }

    @Test
    @DisplayName("Cincuenta millones un guaraní exige Supervisor y Gerente")
    void cincuentaMillonesUnGuaraniExigeSupervisorYGerente() throws Exception {
        verificarRuta(
            50_000_001,
            "NORMAL",
            "SUPERVISOR",
            "GERENTE"
        );
    }

    @Test
    @DisplayName("Cien millones exige Supervisor y Gerente")
    void cienMillonesExigeSupervisorYGerente() throws Exception {
        verificarRuta(100_000_000, "NORMAL", "SUPERVISOR", "GERENTE");
    }

    @Test
    @DisplayName("Cien millones con riesgo alto exige tres ramas sin duplicar Riesgos")
    void cienMillonesAltoExigeTresRamas() throws Exception {
        JsonNode creada = verificarRuta(
            100_000_000,
            "SENSIBLE",
            "SUPERVISOR",
            "GERENTE",
            "RIESGOS"
        );
        String procesoId = identificadorProceso(creada.get("id").asText());

        assertThat(rolesTareasMotor(procesoId))
            .filteredOn("tareaRiesgos"::equals)
            .hasSize(1);
    }

    @Test
    @DisplayName("Doscientos millones exige Supervisor y Gerente")
    void doscientosMillonesExigeSupervisorYGerente() throws Exception {
        verificarRuta(200_000_000, "NORMAL", "SUPERVISOR", "GERENTE");
    }

    @Test
    @DisplayName("Doscientos millones con riesgo alto exige tres ramas")
    void doscientosMillonesAltoExigeTresRamas() throws Exception {
        JsonNode creada = verificarRuta(
            200_000_000,
            "SENSIBLE",
            "SUPERVISOR",
            "GERENTE",
            "RIESGOS"
        );
        String solicitudId = creada.get("id").asText();
        String procesoId = identificadorProceso(solicitudId);

        assertThat(creada.get("requiereEvaluacionExterna").asBoolean()).isFalse();
        assertThat(contarSuscripcionesRiesgo(procesoId)).isZero();
        assertThat(rolesTareasNegocio(solicitudId))
            .containsExactly("GERENTE", "RIESGOS", "SUPERVISOR");
    }

    @Test
    @DisplayName("Doscientos millones un guaraní exige tres ramas y espera externa")
    void doscientosMillonesUnGuaraniEsperaRiesgoExterno() throws Exception {
        JsonNode creada = verificarRuta(
            200_000_001,
            "NORMAL",
            "SUPERVISOR",
            "GERENTE",
            "RIESGOS"
        );

        assertThat(creada.get("requiereEvaluacionExterna").asBoolean()).isTrue();
        assertThat(observaciones(creada.get("idRevision").asText())).isEmpty();
    }

    @Test
    @DisplayName("Doscientos cincuenta millones exige tres ramas y espera externa")
    void doscientosCincuentaMillonesEsperaRiesgoExterno() throws Exception {
        JsonNode creada = verificarRuta(
            250_000_000,
            "NORMAL",
            "SUPERVISOR",
            "GERENTE",
            "RIESGOS"
        );

        assertThat(creada.get("requiereEvaluacionExterna").asBoolean()).isTrue();
        assertThat(observaciones(creada.get("idRevision").asText())).isEmpty();
    }

    private String claveTarea(String rol) {
        return switch (rol) {
            case "SUPERVISOR" -> "tareaSupervisor";
            case "GERENTE" -> "tareaGerente";
            case "RIESGOS" -> "tareaRiesgos";
            default -> throw new IllegalArgumentException("Rol desconocido: " + rol);
        };
    }

    private JsonNode verificarRuta(
        long monto,
        String tipoOperacion,
        String... rolesEsperados
    ) throws Exception {
        JsonNode creada = crear("demo", monto, tipoOperacion);
        String solicitudId = creada.get("id").asText();
        String procesoId = identificadorProceso(solicitudId);
        String[] clavesEsperadas = Arrays.stream(rolesEsperados)
            .map(this::claveTarea)
            .toArray(String[]::new);

        assertThat(rolesTareasMotor(procesoId))
            .as("tareas del motor para %s %s", monto, tipoOperacion)
            .containsExactlyInAnyOrder(clavesEsperadas);
        assertThat(rolesTareasNegocio(solicitudId))
            .as("tareas de negocio para %s %s", monto, tipoOperacion)
            .containsExactlyInAnyOrder(rolesEsperados);

        if (rolesEsperados.length == 0) {
            assertThat(creada.get("estado").asText()).isEqualTo("APROBADA");
            assertThat(procesoActivo(procesoId)).isFalse();
            assertThat(procesoFinalizado(procesoId)).isTrue();
        } else {
            assertThat(creada.get("estado").asText())
                .isEqualTo("PENDIENTE_APROBACION");
            assertThat(procesoActivo(procesoId)).isTrue();
        }

        return creada;
    }
}
