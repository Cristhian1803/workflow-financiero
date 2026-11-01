package com.workflow.financiero.dominio.estado;

import java.util.UUID;

/**
 * Datos del actor y del motivo que acompañan un cambio de estado. La bandera
 * de riesgo manual representa la verificación que hará el llamador cuando la
 * evaluación de riesgo esté implementada.
 */
public record ContextoTransicion(
    UUID actorId,
    String rolCodigo,
    String correlacion,
    String detalle,
    boolean revisionManualDeRiesgo
) {

    public ContextoTransicion(
        UUID actorId,
        String rolCodigo,
        String correlacion,
        String detalle
    ) {
        this(actorId, rolCodigo, correlacion, detalle, false);
    }
}
