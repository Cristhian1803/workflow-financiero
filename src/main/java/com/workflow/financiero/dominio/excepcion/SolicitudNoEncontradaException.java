package com.workflow.financiero.dominio.excepcion;

import java.util.NoSuchElementException;

/**
 * La solicitud no existe o el usuario no tiene visibilidad sobre ella. No se
 * distingue entre ambos casos para no revelar recursos ajenos.
 */
public class SolicitudNoEncontradaException extends NoSuchElementException {

    public SolicitudNoEncontradaException() {
        super("No se encontró la solicitud");
    }
}
