package com.workflow.financiero.dominio.excepcion;

/**
 * El rol indicado no existe, no está activo o no es un rol de aprobación.
 */
public class RolAprobadorInvalidoException extends RuntimeException {

    public RolAprobadorInvalidoException(String mensaje) {
        super(mensaje);
    }
}
