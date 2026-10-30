package com.workflow.financiero.dominio.regla.aprobacion;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;

/**
 * Datos que reciben las políticas para calcular el plan de aprobación.
 */
public record ContextoAprobacion(BigDecimal monto, NivelRiesgo nivelRiesgo) {

    public ContextoAprobacion {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (nivelRiesgo == null) {
            throw new IllegalArgumentException("El nivel de riesgo es obligatorio");
        }
    }
}
