package com.workflow.financiero.api.contrato.incidente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio de un reintento operativo.
 */
public record ReintentarIncidentePeticion(
    @NotBlank
    @Size(max = 1000)
    String motivo
) {
}
