package com.workflow.financiero.api.contrato.tarea;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio de un rechazo.
 */
@Schema(description = "Datos para rechazar una tarea")
public record RechazarTareaPeticion(
    @Schema(
        description = "Motivo obligatorio, máximo 1000 caracteres",
        example = "El monto no coincide con la documentación"
    )
    @NotBlank(message = "El motivo es obligatorio")
    @Size(
        max = 1000,
        message = "El motivo no puede superar los 1000 caracteres"
    )
    String motivo
) {
}
