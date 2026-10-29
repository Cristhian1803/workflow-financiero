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
    EJECUTADA
}
