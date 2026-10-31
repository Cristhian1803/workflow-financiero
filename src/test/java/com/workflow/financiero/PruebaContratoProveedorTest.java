package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.financiero.api.contrato.proveedor.OperacionProveedorFinancieroPeticion;
import com.workflow.financiero.aplicacion.simulador.ModoFalloSimulador;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato observable del proveedor simulado: idempotencia, conflicto,
 * estados, consulta, fallos controlados y concurrencia.
 */
class PruebaContratoProveedorTest extends PruebaProveedorFinancieroBase {

    @BeforeEach
    void prepararSimulador() {
        limpiarSimulador();
    }

    @AfterEach
    void limpiarSimuladorDespues() {
        limpiarSimulador();
    }

    @Test
    @DisplayName("Dos reservas idénticas producen una sola reserva")
    void dosReservasIdenticasProducenUnSoloEfecto() throws Exception {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);

        String primera = referenciaDe(reservar(operacion)
            .andExpect(status().isOk())
            .andReturn());
        String segunda = referenciaDe(reservar(operacion)
            .andExpect(status().isOk())
            .andReturn());

        assertThat(segunda).isEqualTo(primera);
        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
        assertThat(estado(operacion.clave())).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("La misma clave con otra huella produce conflicto")
    void mismaClaveConHuellaDistintaGeneraConflicto() throws Exception {
        OperacionFinanciera original = operacion(AccionFinanciera.RESERVA);
        reservar(original).andExpect(status().isOk());

        OperacionFinanciera cambiada = operacion(
            AccionFinanciera.RESERVA, MONTO.add(BigDecimal.ONE));
        reservar(cambiada)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CONFLICTO_DATOS"));

        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
        assertThat(monto(original.clave())).isEqualByComparingTo(MONTO);
    }

    @Test
    @DisplayName("Una clave distinta para la misma intención es rechazada")
    void claveDistintaParaLaMismaIntencionEsRechazada() throws Exception {
        OperacionFinanciera original = operacion(AccionFinanciera.RESERVA);
        reservar(original).andExpect(status().isOk());

        OperacionProveedorFinancieroPeticion peticion =
            new OperacionProveedorFinancieroPeticion(
                original.solicitudId(),
                original.revisionId(),
                original.accion(),
                "reserva:otra:clave",
                original.huella(),
                original.monto(),
                original.moneda(),
                original.tipoOperacion()
            );
        postConSecreto("/reservas", peticion)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
    }

    @Test
    @DisplayName("La confirmación deja la reserva confirmada y una referencia")
    void confirmacionDespuesDeReserva() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());

        JsonNode respuesta = leer(confirmar(confirmacion)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
            .andReturn());

        assertThat(respuesta.get("referencia").asText())
            .startsWith("CONF-");
        assertThat(estado(reserva.clave())).isEqualTo("CONFIRMADA");
        assertThat(contar(AccionFinanciera.CONFIRMACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una confirmación duplicada devuelve el mismo resultado")
    void confirmacionDuplicadaDevuelveMismoResultado() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());

        String primera = referenciaDe(confirmar(confirmacion)
            .andExpect(status().isOk())
            .andReturn());
        String segunda = referenciaDe(confirmar(confirmacion)
            .andExpect(status().isOk())
            .andReturn());

        assertThat(segunda).isEqualTo(primera);
        assertThat(contar(AccionFinanciera.CONFIRMACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("La consulta recupera el estado por clave")
    void consultaPorClaveRecuperaResultado() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());

        consultar(reserva.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESERVADA"));

        confirmar(confirmacion).andExpect(status().isOk());

        consultar(reserva.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        consultar(confirmacion.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
            .andExpect(jsonPath("$.referencia").isNotEmpty());
    }

    @Test
    @DisplayName("La liberación antes de confirmar deja la reserva liberada")
    void liberacionAntesDeConfirmar() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera liberacion = operacion(
            AccionFinanciera.LIBERACION);
        reservar(reserva).andExpect(status().isOk());

        liberar(liberacion)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("LIBERADA"));

        assertThat(estado(reserva.clave())).isEqualTo("LIBERADA");
        assertThat(contar(AccionFinanciera.LIBERACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una liberación duplicada devuelve el mismo resultado")
    void liberacionDuplicadaDevuelveMismoResultado() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera liberacion = operacion(
            AccionFinanciera.LIBERACION);
        reservar(reserva).andExpect(status().isOk());

        String primera = referenciaDe(liberar(liberacion)
            .andExpect(status().isOk())
            .andReturn());
        String segunda = referenciaDe(liberar(liberacion)
            .andExpect(status().isOk())
            .andReturn());

        assertThat(segunda).isEqualTo(primera);
        assertThat(contar(AccionFinanciera.LIBERACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("Después de confirmar no se puede liberar")
    void liberacionDespuesDeConfirmarEsRechazada() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        OperacionFinanciera liberacion = operacion(
            AccionFinanciera.LIBERACION);
        reservar(reserva).andExpect(status().isOk());
        confirmar(confirmacion).andExpect(status().isOk());

        liberar(liberacion)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CONFLICTO_DATOS"));

        assertThat(estado(reserva.clave())).isEqualTo("CONFIRMADA");
        assertThat(contar(AccionFinanciera.LIBERACION)).isZero();
    }

    @Test
    @DisplayName("Después de liberar no se puede confirmar")
    void confirmacionDespuesDeLiberarEsRechazada() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera liberacion = operacion(
            AccionFinanciera.LIBERACION);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());
        liberar(liberacion).andExpect(status().isOk());

        confirmar(confirmacion)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CONFLICTO_DATOS"));

        assertThat(contar(AccionFinanciera.CONFIRMACION)).isZero();
    }

    @Test
    @DisplayName("El fallo antes del efecto no crea ninguna operación")
    void falloAntesDelEfectoNoCreaOperacion() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        fallos.configurar(
            reserva.clave(),
            ModoFalloSimulador.FALLO_ANTES_DEL_EFECTO
        );

        reservar(reserva)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));

        assertThat(contar(AccionFinanciera.RESERVA)).isZero();

        fallos.limpiar();
        reservar(reserva).andExpect(status().isOk());
        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una confirmación con respuesta perdida conserva el efecto")
    void confirmacionConRespuestaPerdidaConservaEfecto() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());

        fallos.configurar(
            confirmacion.clave(),
            ModoFalloSimulador.RESPUESTA_PERDIDA
        );
        confirmar(confirmacion)
            .andExpect(status().isInternalServerError());

        assertThat(estado(reserva.clave())).isEqualTo("CONFIRMADA");
        assertThat(contar(AccionFinanciera.CONFIRMACION)).isEqualTo(1);

        consultar(confirmacion.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        fallos.limpiar();
        String referencia = referencia(confirmacion.clave());
        assertThat(referenciaDe(confirmar(confirmacion)
            .andExpect(status().isOk())
            .andReturn())).isEqualTo(referencia);
        assertThat(contar(AccionFinanciera.CONFIRMACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("La consulta no disponible se recupera al limpiar el fallo")
    void consultaNoDisponibleSeRecupera() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        reservar(reserva).andExpect(status().isOk());

        fallos.configurar(
            reserva.clave(),
            ModoFalloSimulador.CONSULTA_NO_DISPONIBLE
        );
        consultar(reserva.clave())
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));

        fallos.limpiar();
        consultar(reserva.clave())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESERVADA"));
    }

    @Test
    @DisplayName("Una liberación fallida no declara liberada la reserva")
    void liberacionFallidaNoDeclaraLiberada() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera liberacion = operacion(
            AccionFinanciera.LIBERACION);
        reservar(reserva).andExpect(status().isOk());

        fallos.configurar(
            liberacion.clave(),
            ModoFalloSimulador.LIBERACION_FALLIDA
        );
        liberar(liberacion)
            .andExpect(status().isInternalServerError());

        assertThat(estado(reserva.clave())).isEqualTo("RESERVADA");
        assertThat(contar(AccionFinanciera.LIBERACION)).isZero();

        fallos.limpiar();
        liberar(liberacion).andExpect(status().isOk());
        assertThat(contar(AccionFinanciera.LIBERACION)).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Dos reservas concurrentes producen un solo efecto")
    void repeticionConcurrenteDeReservaProduceUnSoloEfecto() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        ConcurrentLinkedQueue<RespuestaHttp> respuestas =
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
                        MvcResult resultado = reservar(reserva).andReturn();
                        respuestas.add(new RespuestaHttp(
                            resultado.getResponse().getStatus(),
                            resultado.getResponse().getContentAsString()
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

        assertThat(errores).isEmpty();
        assertThat(respuestas).hasSize(2);
        assertThat(respuestas).allMatch(respuesta -> respuesta.estado() == 200);
        assertThat(respuestas.stream()
            .map(respuesta -> referenciaDeCuerpo(respuesta.cuerpo()))
            .distinct()
            .count()).isEqualTo(1);
        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("Dos confirmaciones concurrentes producen un solo efecto")
    void repeticionConcurrenteDeConfirmacionProduceUnSoloEfecto()
        throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        OperacionFinanciera confirmacion = operacion(
            AccionFinanciera.CONFIRMACION);
        reservar(reserva).andExpect(status().isOk());

        ConcurrentLinkedQueue<RespuestaHttp> respuestas =
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
                        MvcResult resultado = confirmar(confirmacion)
                            .andReturn();
                        respuestas.add(new RespuestaHttp(
                            resultado.getResponse().getStatus(),
                            resultado.getResponse().getContentAsString()
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

        assertThat(errores).isEmpty();
        assertThat(respuestas).hasSize(2);
        assertThat(respuestas).allMatch(respuesta -> respuesta.estado() == 200);
        assertThat(respuestas.stream()
            .map(respuesta -> referenciaDeCuerpo(respuesta.cuerpo()))
            .distinct()
            .count()).isEqualTo(1);
        assertThat(contar(AccionFinanciera.CONFIRMACION)).isEqualTo(1);
    }

    @Test
    @DisplayName("El simulador exige la clave de integración")
    void simuladorExigeClaveDeIntegracion() throws Exception {
        OperacionFinanciera reserva = operacion(AccionFinanciera.RESERVA);
        String cuerpo = objectMapper.writeValueAsString(peticion(reserva));

        mockMvc.perform(post(RUTA + "/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post(RUTA + "/reservas")
                .header(CABECERA, "clave-incorrecta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isUnauthorized());

        reservar(reserva).andExpect(status().isOk());
        assertThat(contar(AccionFinanciera.RESERVA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una clave inexistente devuelve 404")
    void consultaDeClaveInexistenteDevuelve404() throws Exception {
        consultar("reserva:00000000-0000-0000-0000-000000000099:"
                + "00000000-0000-0000-0000-000000000098")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    private String referenciaDe(MvcResult resultado) throws Exception {
        return leer(resultado).get("referencia").asText();
    }

    private String referenciaDeCuerpo(String cuerpo) {
        try {
            return objectMapper.readTree(cuerpo).get("referencia").asText();
        } catch (Exception error) {
            throw new IllegalStateException("Respuesta inválida", error);
        }
    }

    private record RespuestaHttp(int estado, String cuerpo) {
    }
}
