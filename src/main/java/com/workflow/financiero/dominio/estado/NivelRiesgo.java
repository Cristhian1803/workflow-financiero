package com.workflow.financiero.dominio.estado;

import java.util.Objects;

/**
 * Niveles de riesgo que puede tener una revisión. El orden del enum va de
 * menor a mayor severidad.
 */
public enum NivelRiesgo {
    BAJO,
    MEDIO,
    ALTO;

    public NivelRiesgo mayor(NivelRiesgo otro) {
        Objects.requireNonNull(otro, "El nivel de riesgo a comparar es obligatorio");
        return compareTo(otro) >= 0 ? this : otro;
    }
}
