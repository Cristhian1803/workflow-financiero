package com.workflow.financiero.aplicacion.servicio;

import java.util.List;

/**
 * Página de resultados con los metadatos que expone la API.
 */
public record PaginaSolicitudes<T>(
    List<T> contenido,
    int pagina,
    int tamano,
    long totalElementos,
    int totalPaginas
) {

    public PaginaSolicitudes {
        contenido = List.copyOf(contenido);
    }
}
