package com.workflow.financiero.dominio.excepcion;

/**
 * El evento externo no cumple el contrato de contenido o de frescura.
 */
public class EventoExternoInvalidoException extends RuntimeException {

    public EventoExternoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
