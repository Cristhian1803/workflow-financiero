package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprueba que el proceso real active solo las ramas del plan para cada
 * combinación de monto y riesgo.
 */
class PruebaRutasProcesoTest extends PruebaProcesoAprobacionBase {

    @Test
    @DisplayName("Cinco millones recorre la vía automática sin tareas")
    void cincoMillonesSinTareas() throws Exception {
        JsonNode creada = verificarRuta(5_000_000, "NORMAL");

        String revisionId = creada.get("idRevision").asText();
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");
        assertThat(claveIntento(revisionId)).isNotBlank();
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

        assertThat(estadoRevision(revisionId)).isEqualTo("EJECUTANDO");
        assertThat(procesoActivo(procesoId)).isTrue();
        assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
        assertThat(estadoIntento(revisionId)).isEqualTo("PENDIENTE");
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
        assertThat(contarIntentos(creada.get("idRevision").asText())).isZero();
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
        assertThat(contarIntentos(creada.get("idRevision").asText())).isZero();
    }

    @ParameterizedTest(name = "{0} mantiene la ruta esperada")
    @MethodSource("bordesInferiores")
    @DisplayName("Los bordes inferiores de monto mantienen la ruta")
    void bordesInferioresMantienenLaRuta(long monto, String[] roles)
        throws Exception {
        verificarRuta(monto, "NORMAL", roles);
    }

    static Stream<Arguments> bordesInferiores() {
        return Stream.of(
            Arguments.of(9_999_999L, new String[] {}),
            Arguments.of(49_999_999L, new String[] {"SUPERVISOR"}),
            Arguments.of(199_999_999L, new String[] {"SUPERVISOR", "GERENTE"})
        );
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
            assertThat(creada.get("estado").asText()).isEqualTo("EJECUTANDO");
            assertThat(procesoActivo(procesoId)).isTrue();
            assertThat(contarSuscripcionesEjecucion(procesoId)).isEqualTo(1);
            assertThat(estadoIntento(creada.get("idRevision").asText()))
                .isEqualTo("PENDIENTE");
        } else {
            assertThat(creada.get("estado").asText())
                .isEqualTo("PENDIENTE_APROBACION");
            assertThat(procesoActivo(procesoId)).isTrue();
        }

        return creada;
    }
}
