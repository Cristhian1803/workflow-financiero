package com.workflow.financiero.aplicacion.servicio;

import java.time.Instant;

/**
 * Token emitido y el instante exacto en que deja de ser válido.
 */
public record TokenEmitido(String token, Instant expiraEn) {

    public TokenEmitido {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token emitido es obligatorio");
        }
        if (expiraEn == null) {
            throw new IllegalArgumentException("La expiración del token es obligatoria");
        }
    }

    @Override
    public String toString() {
        return "TokenEmitido[token=***, expiraEn=" + expiraEn + "]";
    }
}
