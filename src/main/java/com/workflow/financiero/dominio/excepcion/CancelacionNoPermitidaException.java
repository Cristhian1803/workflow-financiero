package com.workflow.financiero.dominio.excepcion;

/**
 * La revisión no admite la cancelación solicitada: es terminal, ya fue
 * confirmada o su resultado todavía es incierto.
 */
public class CancelacionNoPermitidaException
    extends TransicionEstadoInvalidaException {

    public CancelacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
