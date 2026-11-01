package com.workflow.financiero.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesRiesgoExterno;
import com.workflow.financiero.infraestructura.seguridad.FirmaRiesgoExterno;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida la firma HMAC, el proveedor y la frescura de las notificaciones de
 * riesgo antes de que el cuerpo llegue al controlador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class FiltroFirmaRiesgoExterno extends OncePerRequestFilter {

    public static final String RUTA =
        "/api/v1/integraciones/riesgos/notificacion";
    public static final String CABECERA_PROVEEDOR = "X-Proveedor";
    public static final String CABECERA_EVENTO = "X-Identificador-Evento";
    public static final String CABECERA_TIMESTAMP = "X-Timestamp";
    public static final String CABECERA_FIRMA = "X-Firma";
    public static final String ATRIBUTO_CUERPO =
        "riesgoExterno.cuerpoCrudo";

    private static final int LONGITUD_MAXIMA_CUERPO = 1_000_000;

    private final PropiedadesRiesgoExterno propiedades;
    private final Clock reloj;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RUTA.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] cuerpo = request.getInputStream().readAllBytes();
        if (cuerpo.length > LONGITUD_MAXIMA_CUERPO) {
            rechazar(
                response,
                request,
                CodigoError.EVENTO_EXTERNO_INVALIDO,
                "El cuerpo del evento es demasiado grande"
            );
            return;
        }

        String proveedor = request.getHeader(CABECERA_PROVEEDOR);
        String evento = request.getHeader(CABECERA_EVENTO);
        String timestamp = request.getHeader(CABECERA_TIMESTAMP);
        String firma = request.getHeader(CABECERA_FIRMA);
        String secreto = propiedades.secreto();

        if (secreto == null || secreto.isBlank()
            || vacio(proveedor) || vacio(evento)
            || vacio(timestamp) || vacio(firma)) {
            rechazar(
                response,
                request,
                CodigoError.NO_AUTENTICADO,
                "La notificación no tiene una firma válida"
            );
            return;
        }
        if (!propiedades.proveedor().equals(proveedor)) {
            rechazar(
                response,
                request,
                CodigoError.NO_AUTENTICADO,
                "El proveedor de la notificación no es el esperado"
            );
            return;
        }

        Instant instante = instante(timestamp);
        if (instante == null || fueraDeVentana(instante)) {
            rechazar(
                response,
                request,
                CodigoError.EVENTO_EXTERNO_INVALIDO,
                "La marca de tiempo de la notificación está fuera de la ventana"
            );
            return;
        }

        String esperada = FirmaRiesgoExterno.firmar(
            secreto, proveedor, evento, timestamp, cuerpo);
        if (!FirmaRiesgoExterno.firmaValida(esperada, firma)) {
            rechazar(
                response,
                request,
                CodigoError.NO_AUTENTICADO,
                "La firma de la notificación no es válida"
            );
            return;
        }

        request.setAttribute(ATRIBUTO_CUERPO, cuerpo);
        filterChain.doFilter(new PeticionConCuerpo(request, cuerpo), response);
    }

    private Instant instante(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException excepcion) {
            return null;
        }
    }

    private boolean fueraDeVentana(Instant instante) {
        Duration diferencia = Duration.between(instante, reloj.instant()).abs();
        return diferencia.compareTo(propiedades.ventanaTiempo()) > 0;
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private void rechazar(
        HttpServletResponse response,
        HttpServletRequest request,
        CodigoError codigo,
        String detalle
    ) throws IOException {
        ProblemDetail problema = ProblemaApi.crear(codigo, detalle, request);
        response.setStatus(codigo.getEstado().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problema);
    }

    private static final class PeticionConCuerpo
        extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        private PeticionConCuerpo(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new FlujoCuerpo(new ByteArrayInputStream(cuerpo));
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cuerpo), StandardCharsets.UTF_8));
        }
    }

    private static final class FlujoCuerpo extends ServletInputStream {

        private final ByteArrayInputStream entrada;

        private FlujoCuerpo(ByteArrayInputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public int read() {
            return entrada.read();
        }

        @Override
        public boolean isFinished() {
            return entrada.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException(
                "La lectura asíncrona no está soportada");
        }
    }
}
