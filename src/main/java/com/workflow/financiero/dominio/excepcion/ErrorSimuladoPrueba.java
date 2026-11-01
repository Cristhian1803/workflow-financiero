package com.workflow.financiero.dominio.excepcion;

/**
 * Excepcion usada en pruebas para forzar el rollback de una transaccion.
 */
public class ErrorSimuladoPrueba extends RuntimeException {

    public ErrorSimuladoPrueba() {
        super("Fallo simulado para validar el rollback");
    }
}
