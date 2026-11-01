package com.workflow.financiero.dominio.estado;

/**
 * Ciclo de vida de una evaluación de riesgo. Una evaluación externa nace
 * PENDIENTE y solo pasa a RECIBIDA cuando su resultado se aplica.
 */
public enum EstadoEvaluacionRiesgo {
    PENDIENTE,
    RECIBIDA,
    VENCIDA,
    MANUAL,
    OBSOLETA
}
