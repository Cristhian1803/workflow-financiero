package com.workflow.financiero.dominio.excepcion;

/**
 * Una política de aprobación produjo un rol que no cumple las condiciones del
 * catálogo. Es un defecto de configuración, no una decisión del usuario.
 */
public class PoliticaAprobacionInvalidaException extends RuntimeException {

    public PoliticaAprobacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
