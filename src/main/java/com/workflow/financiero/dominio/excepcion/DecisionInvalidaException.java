package com.workflow.financiero.dominio.excepcion;

/**
 * La decisión no cumple las validaciones de su contrato, por ejemplo un
 * rechazo sin motivo o con un texto demasiado largo.
 */
public class DecisionInvalidaException extends RuntimeException {

    private final String campo;

    public DecisionInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
