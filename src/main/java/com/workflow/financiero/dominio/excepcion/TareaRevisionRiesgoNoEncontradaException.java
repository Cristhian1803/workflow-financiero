package com.workflow.financiero.dominio.excepcion;

/**
 * La tarea de revisión manual no existe o no es visible para el actor.
 */
public class TareaRevisionRiesgoNoEncontradaException extends RuntimeException {

    public TareaRevisionRiesgoNoEncontradaException() {
        super("No se encontró la tarea de revisión de riesgo");
    }

    public TareaRevisionRiesgoNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
