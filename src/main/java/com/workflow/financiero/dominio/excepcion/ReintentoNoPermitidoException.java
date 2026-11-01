package com.workflow.financiero.dominio.excepcion;

/**
 * El incidente o la intención no admiten un reintento seguro.
 */
public class ReintentoNoPermitidoException extends RuntimeException {

    public ReintentoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
