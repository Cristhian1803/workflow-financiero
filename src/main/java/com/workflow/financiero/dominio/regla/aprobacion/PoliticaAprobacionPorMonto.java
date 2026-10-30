package com.workflow.financiero.dominio.regla.aprobacion;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Aprobaciones exigidas por tramo de monto. Los límites son inclusivos.
 */
public class PoliticaAprobacionPorMonto implements PoliticaAprobacion {

    public static final BigDecimal LIMITE_SIN_APROBACION = new BigDecimal("10000000");
    public static final BigDecimal LIMITE_SUPERVISOR = new BigDecimal("50000000");
    public static final BigDecimal LIMITE_MULTIPLES_APROBACIONES = new BigDecimal("200000000");

    @Override
    public Set<String> rolesRequeridos(ContextoAprobacion contexto) {
        Objects.requireNonNull(contexto, "El contexto de aprobación es obligatorio");

        BigDecimal monto = contexto.monto();
        if (monto.compareTo(LIMITE_SIN_APROBACION) <= 0) {
            return Set.of();
        }
        if (monto.compareTo(LIMITE_SUPERVISOR) <= 0) {
            return Set.of("SUPERVISOR");
        }
        if (monto.compareTo(LIMITE_MULTIPLES_APROBACIONES) <= 0) {
            return Set.of("SUPERVISOR", "GERENTE");
        }
        return Set.of("SUPERVISOR", "GERENTE", "RIESGOS");
    }
}
