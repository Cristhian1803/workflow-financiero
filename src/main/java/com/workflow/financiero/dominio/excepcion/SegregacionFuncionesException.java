package com.workflow.financiero.dominio.excepcion;

/**
 * El actor ya aprobó un rol funcional distinto dentro de la misma solicitud.
 */
public class SegregacionFuncionesException extends RuntimeException {

    public SegregacionFuncionesException(String mensaje) {
        super(mensaje);
    }
}
