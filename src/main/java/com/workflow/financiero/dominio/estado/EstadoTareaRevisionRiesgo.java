package com.workflow.financiero.dominio.estado;

/**
 * Estados de la tarea humana de revisión manual de riesgo.
 */
public enum EstadoTareaRevisionRiesgo {
    PENDIENTE,
    RESUELTA,
    CANCELADA;

    public boolean esFinal() {
        return this != PENDIENTE;
    }
}
