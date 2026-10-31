package com.workflow.financiero.dominio.excepcion;

/**
 * La solicitud no cumple las condiciones de plazo o cantidad de revisiones
 * para volver a enviarse.
 */
public class ReenvioNoPermitidoException extends RuntimeException {

    public ReenvioNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
