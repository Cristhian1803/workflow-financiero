package com.workflow.financiero.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;

/**
 * Construye respuestas de error con el formato común de la API.
 */
public final class ProblemaApi {

    private ProblemaApi() {
    }

    public static ProblemDetail crear(
        CodigoError codigo,
        String detalle,
        HttpServletRequest peticion
    ) {
        return crear(codigo, detalle, peticion, Correlacion.obtener(peticion));
    }

    public static ProblemDetail crear(
        CodigoError codigo,
        String detalle,
        HttpServletRequest peticion,
        String correlacion
    ) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            codigo.getEstado(), detalle);
        problema.setTitle(codigo.getTitulo());
        problema.setInstance(URI.create(peticion.getRequestURI()));
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("identificadorCorrelacion", correlacion);
        return problema;
    }

    public static ProblemDetail conErrores(
        ProblemDetail problema,
        List<ErrorCampoRespuesta> errores
    ) {
        if (!errores.isEmpty()) {
            problema.setProperty("errores", List.copyOf(errores));
        }
        return problema;
    }
}
