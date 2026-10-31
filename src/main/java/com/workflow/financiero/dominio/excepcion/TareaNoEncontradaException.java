package com.workflow.financiero.dominio.excepcion;

/**
 * La tarea de aprobación no existe o no es visible para el actor.
 */
public class TareaNoEncontradaException extends RuntimeException {

    public TareaNoEncontradaException() {
        super("No se encontró la tarea de aprobación");
    }

    public TareaNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
