package com.workflow.financiero.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesProveedorFinanciero;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige una clave de integración para las rutas del simulador. La comparación
 * es en tiempo constante y el valor nunca se registra en logs.
 */
@Component
@Profile({"desarrollo", "pruebas"})
@RequiredArgsConstructor
public class FiltroSecretoProveedorFinanciero extends OncePerRequestFilter {

    public static final String CABECERA = "X-Clave-Integracion";
    private static final String RUTA_SIMULADOR =
        "/api/v1/integraciones/proveedor-financiero";

    private final PropiedadesProveedorFinanciero propiedades;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(RUTA_SIMULADOR);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!secretoValido(request.getHeader(CABECERA))) {
            ProblemDetail problema = ProblemaApi.crear(
                CodigoError.NO_AUTENTICADO,
                "Se requiere una clave de integración válida",
                request
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), problema);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean secretoValido(String recibido) {
        String esperado = propiedades.secreto();
        if (esperado == null || esperado.isBlank() || recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
            recibido.getBytes(StandardCharsets.UTF_8),
            esperado.getBytes(StandardCharsets.UTF_8)
        );
    }
}
