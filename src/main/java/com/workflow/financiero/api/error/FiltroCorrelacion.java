package com.workflow.financiero.api.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asigna la correlación antes de la seguridad para que también alcance a los
 * errores 401 y 403. El MDC se limpia siempre al terminar la petición.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest peticion,
        HttpServletResponse respuesta,
        FilterChain cadena
    ) throws ServletException, IOException {
        String correlacion = Correlacion.resolver(
            peticion.getHeader(Correlacion.CABECERA));

        peticion.setAttribute(Correlacion.ATRIBUTO, correlacion);
        respuesta.setHeader(Correlacion.CABECERA, correlacion);
        MDC.put(Correlacion.CLAVE_MDC, correlacion);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(Correlacion.CLAVE_MDC);
        }
    }
}
