package com.workflow.financiero.dominio.temporal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Genera instantes con la precisión que conserva PostgreSQL en columnas
 * TIMESTAMP WITH TIME ZONE, de modo que el valor leído sea igual al guardado.
 */
public final class Instantes {

    private Instantes() {
    }

    public static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
