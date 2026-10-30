package com.workflow.financiero.dominio.excepcion;

/**
 * La tarea de aprobación no admite el cambio de estado solicitado.
 */
public class TransicionTareaInvalidaException extends RuntimeException {

    public TransicionTareaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
