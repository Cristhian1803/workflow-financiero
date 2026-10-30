package com.workflow.financiero.dominio.evento;

/**
 * Tipos de evento que forman parte del historial de una solicitud.
 */
public enum TipoEventoSolicitud {
    SOLICITUD_CREADA,
    CAMBIO_ESTADO,
    RIESGO_CLASIFICADO,
    PLAN_APROBACION_CALCULADO,
    EVALUACION_EXTERNA_PENDIENTE,
    PROCESO_INICIADO,
    EJECUCION_PREPARADA,
    EJECUCION_CONTROLADA
}
