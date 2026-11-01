package com.workflow.financiero.aplicacion.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del trabajador de ejecución y de la recuperación de resultados
 * inciertos.
 */
@ConfigurationProperties(prefix = "ejecucion.trabajador")
public record PropiedadesEjecucion(
    boolean habilitado,
    long intervaloMs,
    int maxIntentos,
    Duration reclamacionVencida,
    Duration reintentoEspera
) {

    public PropiedadesEjecucion {
        if (intervaloMs <= 0) {
            throw new IllegalStateException(
                "El intervalo del trabajador debe ser mayor que cero");
        }
        if (maxIntentos <= 0) {
            throw new IllegalStateException(
                "El máximo de intentos debe ser mayor que cero");
        }
        if (reclamacionVencida == null || reclamacionVencida.isNegative()) {
            throw new IllegalStateException(
                "El plazo de reclamación no puede ser negativo");
        }
        if (reintentoEspera == null || reintentoEspera.isNegative()) {
            throw new IllegalStateException(
                "La espera de reintento no puede ser negativa");
        }
    }
}
