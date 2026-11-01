package com.workflow.financiero.dominio.excepcion;

/**
 * La revisión intenta pasar a un estado que no está permitido desde el actual.
 */
public class TransicionEstadoInvalidaException extends RuntimeException {

    public TransicionEstadoInvalidaException(String mensaje) {
        super(mensaje);
    }
}
