package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.api.contrato.CrearSolicitudPruebaPeticion;
import com.workflow.financiero.aplicacion.servicio.AdaptadorEjecucionPrueba;
import com.workflow.financiero.aplicacion.servicio.ServicioFlujoMinimo;
import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
import com.workflow.financiero.dominio.excepcion.SolicitudPruebaInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PruebaFlujoMinimoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ServicioFlujoMinimo servicioFlujoMinimo;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Una solicitud valida llega al resultado simulado")
    @WithMockUser(username = "demo")
    void solicitudValidaAlcanzaEjecucionSimulada() throws Exception {
        var respuesta = mockMvc.perform(post("/api/v1/solicitudes-prueba")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CrearSolicitudPruebaPeticion(5_000_000L, "PYG"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value(ServicioFlujoMinimo.ESTADO_EJECUTADA_SIMULADA))
            .andExpect(jsonPath("$.resultadoSimulado").value(AdaptadorEjecucionPrueba.RESULTADO_SIMULADO))
            .andExpect(jsonPath("$.historial.length()").value(2))
            .andReturn();

        String ubicacion = respuesta.getResponse().getHeader("Location");
        assertThat(ubicacion).isNotBlank();

        mockMvc.perform(get(ubicacion).with(user("demo")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value(ServicioFlujoMinimo.ESTADO_EJECUTADA_SIMULADA));
    }

    @Test
    @DisplayName("Una solicitud invalida no inicia el proceso")
    void solicitudInvalidaNoIniciaProceso() {
        assertThatThrownBy(() -> servicioFlujoMinimo.crearSolicitudYProceso(-1L, "PYG", false))
            .isInstanceOf(SolicitudPruebaInvalidaException.class);

        assertThatThrownBy(() -> servicioFlujoMinimo.crearSolicitudYProceso(1_000L, "USD", false))
            .isInstanceOf(SolicitudPruebaInvalidaException.class);
    }

    @Test
    @DisplayName("Un fallo al iniciar deja la tabla de solicitudes intacta")
    void rollbackAlIniciarFlujoMinimo() {
        Long solicitudesAntes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM solicitud_prueba", Long.class);

        assertThatThrownBy(() -> servicioFlujoMinimo.crearSolicitudYProceso(1_000L, "PYG", true))
            .isInstanceOf(ErrorSimuladoPrueba.class);

        Long solicitudesDespues = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM solicitud_prueba", Long.class);
        assertThat(solicitudesDespues).isEqualTo(solicitudesAntes);
    }

    @Test
    @DisplayName("La API responde 400 ante un monto invalido")
    @WithMockUser(username = "demo")
    void apiRechazaSolicitudInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes-prueba")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CrearSolicitudPruebaPeticion(0L, "PYG"))))
            .andExpect(status().isBadRequest());
    }
}
