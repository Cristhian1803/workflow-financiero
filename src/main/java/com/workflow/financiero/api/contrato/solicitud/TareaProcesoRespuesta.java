package com.workflow.financiero.api.contrato.solicitud;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Tarea activa del proceso.
 */
@Schema(description = "Tarea de aprobación del proceso")
public record TareaProcesoRespuesta(
    @Schema(example = "tarea-1")
    String id,

    @Schema(example = "SUPERVISOR")
    String rol,

    @Schema(example = "Aprobación de Supervisor")
    String nombre,

    @Schema(example = "PENDIENTE")
    String estado,

    @Schema(description = "Usuario asignado, si existe", nullable = true)
    String asignadoA,

    @Schema(description = "Vencimiento de la tarea de negocio, si existe",
        nullable = true)
    Instant fechaVencimiento
) {
}
