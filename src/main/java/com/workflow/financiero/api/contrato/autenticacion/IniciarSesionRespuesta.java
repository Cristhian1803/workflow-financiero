package com.workflow.financiero.api.contrato.autenticacion;

import java.time.Instant;

/**
 * Token emitido tras un inicio de sesión correcto.
 */
public record IniciarSesionRespuesta(String token, String tipo, Instant expiraEn) {

    @Override
    public String toString() {
        return "IniciarSesionRespuesta[token=***, tipo=" + tipo
            + ", expiraEn=" + expiraEn + "]";
    }
}
