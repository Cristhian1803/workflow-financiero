package com.workflow.financiero;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesProveedorFinanciero;
import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.infraestructura.externo.AdaptadorProveedorFinancieroHttp;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Pruebas del adaptador HTTP contra WireMock: contrato de transporte y
 * traducción de errores. No demuestran idempotencia del efecto; eso lo cubre
 * la prueba de contrato contra el simulador persistente.
 */
class PruebaAdaptadorProveedorFinancieroTest {

    private static final String RUTA =
        "/api/v1/integraciones/proveedor-financiero";
    private static final String SECRETO = UUID.randomUUID().toString();
    private static final WireMockServer WIREMOCK =
        new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    private AdaptadorProveedorFinancieroHttp adaptador;
    private PropiedadesProveedorFinanciero propiedades;

    @AfterAll
    static void detenerWireMock() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void prepararAdaptador() {
        WIREMOCK.resetAll();
        propiedades = new PropiedadesProveedorFinanciero(
            URI.create(WIREMOCK.baseUrl() + RUTA),
            Duration.ofSeconds(2),
            Duration.ofMillis(200),
            SECRETO
        );
        adaptador = new AdaptadorProveedorFinancieroHttp(propiedades);
    }

    @Test
    @DisplayName("Reservar envía método, ruta, cabecera y cuerpo")
    void reservarEnviaContratoCompleto() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(respuesta(200, operacion, "RESERVADA", "RES-1")));

        ResultadoOperacionFinanciera resultado = adaptador.reservar(operacion);

        assertThat(resultado.clave()).isEqualTo(operacion.clave());
        assertThat(resultado.estado().name()).isEqualTo("RESERVADA");
        assertThat(resultado.referencia()).isEqualTo("RES-1");

        WIREMOCK.verify(postRequestedFor(urlEqualTo(RUTA + "/reservas"))
            .withHeader("X-Clave-Integracion", equalTo(SECRETO))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.clave", equalTo(operacion.clave())))
            .withRequestBody(matchingJsonPath("$.huella", equalTo(operacion.huella())))
            .withRequestBody(matchingJsonPath("$.accion", equalTo("RESERVA"))));
    }

    @Test
    @DisplayName("Confirmar envía método, ruta, cabecera y cuerpo")
    void confirmarEnviaContratoCompleto() {
        OperacionFinanciera operacion = operacion(
            AccionFinanciera.CONFIRMACION);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/confirmaciones"))
            .willReturn(respuesta(200, operacion, "CONFIRMADA", "CONF-1")));

        ResultadoOperacionFinanciera resultado = adaptador.confirmar(operacion);

        assertThat(resultado.estado().name()).isEqualTo("CONFIRMADA");
        assertThat(resultado.referencia()).isEqualTo("CONF-1");
        WIREMOCK.verify(postRequestedFor(urlEqualTo(RUTA + "/confirmaciones"))
            .withHeader("X-Clave-Integracion", equalTo(SECRETO))
            .withRequestBody(matchingJsonPath("$.accion", equalTo("CONFIRMACION"))));
    }

    @Test
    @DisplayName("Liberar envía método, ruta, cabecera y cuerpo")
    void liberarEnviaContratoCompleto() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.LIBERACION);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/liberaciones"))
            .willReturn(respuesta(200, operacion, "LIBERADA", "LIB-1")));

        ResultadoOperacionFinanciera resultado = adaptador.liberar(operacion);

        assertThat(resultado.estado().name()).isEqualTo("LIBERADA");
        assertThat(resultado.referencia()).isEqualTo("LIB-1");
        WIREMOCK.verify(postRequestedFor(urlEqualTo(RUTA + "/liberaciones"))
            .withHeader("X-Clave-Integracion", equalTo(SECRETO))
            .withRequestBody(matchingJsonPath("$.accion", equalTo("LIBERACION"))));
    }

    @Test
    @DisplayName("Consultar recupera el resultado por clave")
    void consultarRecuperaResultado() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(get(urlPathMatching(RUTA + "/operaciones/.*"))
            .willReturn(respuesta(200, operacion, "RESERVADA", "RES-1")));

        ResultadoOperacionFinanciera resultado = adaptador.consultar(
            operacion.clave());

        assertThat(resultado.clave()).isEqualTo(operacion.clave());
        assertThat(resultado.referencia()).isEqualTo("RES-1");
        WIREMOCK.verify(getRequestedFor(urlPathMatching(
                RUTA + "/operaciones/.*"))
            .withHeader("X-Clave-Integracion", equalTo(SECRETO)));
    }

    @Test
    @DisplayName("Un 409 se traduce a conflicto definitivo")
    void conflictoSeTraduceAConflicto() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> adaptador.reservar(operacion))
            .isInstanceOf(ConflictoProveedorFinancieroException.class);
    }

    @Test
    @DisplayName("Un 500 se traduce a recuperable y no se reintenta")
    void error500SeTraduceARecuperableYSinReintento() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adaptador.reservar(operacion))
            .isInstanceOf(ErrorRecuperableProveedorFinancieroException.class);

        WIREMOCK.verify(exactly(1), postRequestedFor(
            urlEqualTo(RUTA + "/reservas")));
    }

    @Test
    @DisplayName("Un timeout se traduce a recuperable")
    void timeoutSeTraduceARecuperable() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(respuesta(200, operacion, "RESERVADA", "RES-1")
                .withFixedDelay(1000)));

        assertThatThrownBy(() -> adaptador.reservar(operacion))
            .isInstanceOf(ErrorRecuperableProveedorFinancieroException.class);
    }

    @Test
    @DisplayName("Un 400 se traduce a error definitivo")
    void error400SeTraduceADefinitivo() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adaptador.reservar(operacion))
            .isInstanceOf(ErrorDefinitivoProveedorFinancieroException.class);
    }

    @Test
    @DisplayName("Un 404 de consulta se traduce a error definitivo")
    void consulta404SeTraduceADefinitivo() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(get(urlPathMatching(RUTA + "/operaciones/.*"))
            .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> adaptador.consultar(operacion.clave()))
            .isInstanceOf(ErrorDefinitivoProveedorFinancieroException.class);
    }

    @Test
    @DisplayName("El adaptador no registra el secreto en logs")
    void noRegistraSecretos() {
        OperacionFinanciera operacion = operacion(AccionFinanciera.RESERVA);
        WIREMOCK.stubFor(post(urlEqualTo(RUTA + "/reservas"))
            .willReturn(aResponse().withStatus(500)));

        Logger logger = (Logger) LoggerFactory.getLogger(
            AdaptadorProveedorFinancieroHttp.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> adaptador.reservar(operacion))
                .isInstanceOf(
                    ErrorRecuperableProveedorFinancieroException.class);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
            .allMatch(evento -> !evento.getFormattedMessage().contains(SECRETO));
        assertThat(propiedades.toString())
            .doesNotContain(SECRETO)
            .contains("***");
    }

    private OperacionFinanciera operacion(AccionFinanciera accion) {
        return OperacionFinanciera.crear(
            UUID.randomUUID(),
            UUID.randomUUID(),
            accion,
            BigDecimal.valueOf(25_000_000),
            "PYG",
            "NORMAL"
        );
    }

    private ResponseDefinitionBuilder respuesta(
        int estado,
        OperacionFinanciera operacion,
        String estadoOperacion,
        String referencia
    ) {
        String cuerpo = """
            {
              "id": "%s",
              "clave": "%s",
              "accion": "%s",
              "estado": "%s",
              "referencia": "%s",
              "huella": "%s",
              "actualizadoEn": "2026-10-31T12:00:00Z"
            }
            """.formatted(
                UUID.randomUUID(),
                operacion.clave(),
                operacion.accion(),
                estadoOperacion,
                referencia,
                operacion.huella()
            );
        return aResponse()
            .withStatus(estado)
            .withHeader("Content-Type", "application/json")
            .withBody(cuerpo);
    }
}
