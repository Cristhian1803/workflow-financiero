package com.workflow.financiero.dominio.estado;

/**
 * Estados del ciclo de vida de una tarea de aprobación.
 */
public enum EstadoTareaAprobacion {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    CANCELADA;

    public boolean esFinal() {
        return this != PENDIENTE;
    }
}
