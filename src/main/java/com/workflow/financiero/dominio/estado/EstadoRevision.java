package com.workflow.financiero.dominio.estado;

/**
 * Estados del ciclo de vida de una revisión de solicitud.
 */
public enum EstadoRevision {
    RECIBIDA,
    VALIDANDO,
    EVALUANDO_RIESGO,
    PENDIENTE_APROBACION,
    APROBADA,
    EJECUTANDO,
    RESULTADO_DESCONOCIDO,
    COMPENSANDO,
    REVISION_MANUAL,
    RECHAZADA,
    CANCELADA,
    EJECUTADA;

    public boolean puedeTransicionarA(EstadoRevision destino) {
        if (destino == null) {
            return false;
        }

        return switch (this) {
            case RECIBIDA -> destino == VALIDANDO || destino == CANCELADA;
            case VALIDANDO -> destino == EVALUANDO_RIESGO || destino == CANCELADA;
            case EVALUANDO_RIESGO -> destino == PENDIENTE_APROBACION
                || destino == APROBADA
                || destino == REVISION_MANUAL
                || destino == CANCELADA;
            case PENDIENTE_APROBACION -> destino == APROBADA
                || destino == RECHAZADA
                || destino == CANCELADA;
            case APROBADA -> destino == EJECUTANDO || destino == CANCELADA;
            case EJECUTANDO -> destino == EJECUTADA
                || destino == RESULTADO_DESCONOCIDO
                || destino == COMPENSANDO
                || destino == REVISION_MANUAL;
            case RESULTADO_DESCONOCIDO -> destino == EJECUTADA
                || destino == COMPENSANDO
                || destino == REVISION_MANUAL;
            case COMPENSANDO -> destino == CANCELADA || destino == REVISION_MANUAL;
            case REVISION_MANUAL -> destino == EVALUANDO_RIESGO;
            case RECHAZADA, CANCELADA, EJECUTADA -> false;
        };
    }

    public boolean esTerminal() {
        return this == RECHAZADA || this == CANCELADA || this == EJECUTADA;
    }
}
