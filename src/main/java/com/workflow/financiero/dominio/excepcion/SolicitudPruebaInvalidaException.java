package com.workflow.financiero.dominio.excepcion;

/**
 * Se lanza cuando los datos de la solicitud no cumplen las reglas minimas.
 */
public class SolicitudPruebaInvalidaException extends RuntimeException {

    public SolicitudPruebaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
