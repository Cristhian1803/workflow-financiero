package com.workflow.financiero.aplicacion.servicio;

import java.util.UUID;

/**
 * Contexto de una pasada de compensación. En el reintento manual conserva el
 * incidente y el actor que autorizó la intervención.
 */
public record ContextoCompensacion(
    UsuarioActual actual,
    String motivo,
    String correlacion,
    UUID incidenteId,
    boolean manual
) {

    public static ContextoCompensacion automatico() {
        return new ContextoCompensacion(
            null,
            "Reconciliación automática de cancelación",
            "recuperacion-compensacion",
            null,
            false
        );
    }

    public static ContextoCompensacion manual(
        UsuarioActual actual,
        String motivo,
        String correlacion,
        UUID incidenteId
    ) {
        return new ContextoCompensacion(
            actual,
            motivo,
            correlacion,
            incidenteId,
            true
        );
    }
}
