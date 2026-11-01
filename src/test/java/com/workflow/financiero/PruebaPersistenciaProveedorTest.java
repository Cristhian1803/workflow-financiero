package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Comprueba que el estado del simulador sobrevive a un reinicio del contexto.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PruebaPersistenciaProveedorTest extends PruebaProveedorFinancieroBase {

    private static String referenciaPersistida;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("La reserva queda persistida antes del reinicio")
    void reservaQuedaPersistidaAntesDelReinicio() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);

        JsonNode respuesta = leer(reservar(reserva)
            .andExpect(status().isOk())
            .andReturn());

        referenciaPersistida = respuesta.get("referencia").asText();
        assertThat(estado(reserva.clave())).isEqualTo("RESERVADA");
    }

    @Test
    @Order(2)
    @DisplayName("La reserva sigue consultable después del reinicio")
    void reservaSigueConsultableDespuesDelReinicio() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);

        consultar(reserva.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESERVADA"))
            .andExpect(jsonPath("$.referencia").value(referenciaPersistida));

        limpiarSimulador();
    }
}
