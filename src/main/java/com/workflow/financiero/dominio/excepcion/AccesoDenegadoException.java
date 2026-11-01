package com.workflow.financiero.dominio.excepcion;

/**
 * El usuario está autenticado pero no tiene el rol necesario para la acción.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
