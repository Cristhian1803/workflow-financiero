package com.workflow.financiero.dominio.excepcion;

/**
 * La tarea ya fue aprobada, rechazada o cancelada.
 */
public class TareaYaResueltaException extends RuntimeException {

    public TareaYaResueltaException() {
        super("La tarea de aprobación ya fue resuelta");
    }

    public TareaYaResueltaException(String mensaje) {
        super(mensaje);
    }
}
