package com.workflow.financiero.dominio.excepcion;

/**
 * El incidente solicitado no existe o no es visible para el operador.
 */
public class IncidenteNoEncontradoException extends RuntimeException {

    public IncidenteNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
