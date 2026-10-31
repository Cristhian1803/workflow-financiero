package com.workflow.financiero.dominio.excepcion;

/**
 * El actor no puede resolver la tarea por su identidad, asignación o rol.
 */
public class AprobacionNoPermitidaException extends RuntimeException {

    public AprobacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
