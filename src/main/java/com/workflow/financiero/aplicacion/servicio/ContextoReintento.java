package com.workflow.financiero.aplicacion.servicio;

import java.util.UUID;

/**
 * Contexto de un reintento manual. En la recuperación automática el actor es
 * nulo y el motivo describe la causa detectada por el trabajador.
 */
public record ContextoReintento(
    UUID incidenteId,
    UsuarioActual actor,
    String motivo,
    String correlacion
) {

    public boolean esManual() {
        return actor != null;
    }
}
