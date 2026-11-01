package com.workflow.financiero.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Traduce los errores emitidos por la cadena de seguridad al mismo contrato
 * que usa el resto de la API.
 */
@Component
@RequiredArgsConstructor
public class ManejadorErroresSeguridad
    implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest peticion,
        HttpServletResponse respuesta,
        AuthenticationException excepcion
    ) throws IOException {
        if (respuesta.isCommitted()) {
            return;
        }
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(
            peticion,
            respuesta,
            CodigoError.NO_AUTENTICADO,
            "Se requiere un token Bearer válido"
        );
    }

    @Override
    public void handle(
        HttpServletRequest peticion,
        HttpServletResponse respuesta,
        AccessDeniedException excepcion
    ) throws IOException {
        if (respuesta.isCommitted()) {
            return;
        }
        escribir(
            peticion,
            respuesta,
            CodigoError.ACCESO_DENEGADO,
            "No tiene permiso para realizar esta operación"
        );
    }

    private void escribir(
        HttpServletRequest peticion,
        HttpServletResponse respuesta,
        CodigoError codigo,
        String detalle
    ) throws IOException {
        String correlacion = Correlacion.obtener(peticion);
        ProblemDetail problema = ProblemaApi.crear(
            codigo, detalle, peticion, correlacion);

        respuesta.setStatus(codigo.getEstado().value());
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setHeader(Correlacion.CABECERA, correlacion);
        objectMapper.writeValue(respuesta.getOutputStream(), problema);
    }
}
