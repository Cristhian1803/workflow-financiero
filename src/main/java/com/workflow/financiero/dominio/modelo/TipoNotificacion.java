package com.workflow.financiero.dominio.modelo;

/**
 * Motivo por el que se genera una notificación de una tarea de aprobación.
 */
public enum TipoNotificacion {
    ESCALAMIENTO_24H,
    REASIGNACION_48H,
    TAREA_ASIGNADA,
    REASIGNACION_FALLIDA
}
