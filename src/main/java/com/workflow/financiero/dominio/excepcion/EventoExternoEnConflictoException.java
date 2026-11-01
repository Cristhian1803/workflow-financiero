package com.workflow.financiero.dominio.excepcion;

/**
 * Llegó otro cuerpo con un identificador de evento ya utilizado.
 */
public class EventoExternoEnConflictoException extends RuntimeException {

    public EventoExternoEnConflictoException(String mensaje) {
        super(mensaje);
    }
}
