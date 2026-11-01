package com.workflow.financiero.dominio.excepcion;

/**
 * La revisión manual ya fue resuelta, cancelada o dejó de ser la vigente.
 */
public class RevisionRiesgoYaResueltaException extends RuntimeException {

    public RevisionRiesgoYaResueltaException(String mensaje) {
        super(mensaje);
    }
}
