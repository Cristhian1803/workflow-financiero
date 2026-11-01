package com.workflow.financiero.api.contrato.solicitud;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio de una cancelación solicitada por el dueño o un
 * administrador.
 */
public record CancelarSolicitudPeticion(
    @NotBlank
    @Size(max = 1000)
    String motivo
) {
}
