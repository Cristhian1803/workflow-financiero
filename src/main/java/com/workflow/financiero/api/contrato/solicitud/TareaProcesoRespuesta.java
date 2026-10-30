package com.workflow.financiero.api.contrato.solicitud;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tarea del proceso. Se completa cuando el proceso BPMN exista; en el Paso 2
 * la lista siempre está vacía.
 */
@Schema(description = "Tarea de aprobación del proceso")
public record TareaProcesoRespuesta(
    @Schema(example = "tarea-1")
    String id,

    @Schema(example = "Aprobación de supervisor")
    String nombre,

    @Schema(example = "PENDIENTE")
    String estado,

    @Schema(example = "supervisor")
    String asignadoA
) {
}
