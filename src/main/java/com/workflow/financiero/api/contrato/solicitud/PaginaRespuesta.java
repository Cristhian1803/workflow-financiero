package com.workflow.financiero.api.contrato.solicitud;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Respuesta paginada con metadatos estables.
 */
@Schema(description = "Página de resultados")
public record PaginaRespuesta<T>(
    @Schema(description = "Elementos de la página")
    List<T> contenido,

    @Schema(example = "0")
    int pagina,

    @Schema(example = "20")
    int tamano,

    @Schema(example = "1")
    long totalElementos,

    @Schema(example = "1")
    int totalPaginas
) {

    public PaginaRespuesta {
        contenido = List.copyOf(contenido);
    }
}
