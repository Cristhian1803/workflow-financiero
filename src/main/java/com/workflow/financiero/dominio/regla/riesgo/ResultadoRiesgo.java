package com.workflow.financiero.dominio.regla.riesgo;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.util.Objects;

/**
 * Resultado de clasificar o consolidar el riesgo. Cuando
 * {@code requiereRevisionManual} es verdadero, el resultado no alcanza para
 * autorizar la operación.
 */
public record ResultadoRiesgo(
    NivelRiesgo nivel,
    OrigenRiesgo origen,
    boolean requiereEvaluacionExterna,
    boolean requiereRevisionManual,
    String detalle
) {

    public ResultadoRiesgo {
        Objects.requireNonNull(nivel, "El nivel de riesgo es obligatorio");
        Objects.requireNonNull(origen, "El origen del riesgo es obligatorio");
        if (detalle == null || detalle.isBlank()) {
            throw new IllegalArgumentException("El detalle del riesgo es obligatorio");
        }
    }
}
