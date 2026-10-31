package com.workflow.financiero.api.contrato.tarea;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Comentario opcional que acompaña una aprobación.
 */
@Schema(description = "Datos para aprobar una tarea")
public record AprobarTareaPeticion(
    @Schema(
        description = "Comentario opcional, máximo 1000 caracteres",
        example = "Aprobado para continuar"
    )
    @Size(
        max = 1000,
        message = "El comentario no puede superar los 1000 caracteres"
    )
    String comentario
) {
}
