package com.workflow.financiero.aplicacion.excepcion;

/**
 * Las credenciales no permiten autenticar al usuario. El mensaje es genérico
 * para no revelar si el usuario existe o está activo.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o clave inválidos");
    }
}
