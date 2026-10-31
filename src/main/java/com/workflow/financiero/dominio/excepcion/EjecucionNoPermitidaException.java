package com.workflow.financiero.dominio.excepcion;

/**
 * La revisión no cumple las condiciones para ejecutar o reintentar.
 */
public class EjecucionNoPermitidaException extends RuntimeException {

    public EjecucionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
