package com.workflow.financiero.dominio.regla.riesgo;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Política interna de demostración. El monto debe haber pasado la validación
 * de entrada antes de llamar a este clasificador.
 */
public class ClasificadorRiesgoInterno {

    private static final BigDecimal LIMITE_BAJO = new BigDecimal("50000000");
    private static final BigDecimal LIMITE_MEDIO = new BigDecimal("200000000");
    private static final String PERFIL_ALTO = "ALTO";

    public ResultadoRiesgo clasificar(
        TipoOperacion tipoOperacion,
        PerfilRiesgo perfilSolicitante,
        BigDecimal monto
    ) {
        Objects.requireNonNull(tipoOperacion, "El tipo de operación es obligatorio");
        Objects.requireNonNull(perfilSolicitante, "El perfil del solicitante es obligatorio");
        Objects.requireNonNull(monto, "El monto es obligatorio");

        NivelRiesgo base;
        String reglaBase;
        if (monto.compareTo(LIMITE_BAJO) <= 0) {
            base = NivelRiesgo.BAJO;
            reglaBase = "monto <= 50000000";
        } else if (monto.compareTo(LIMITE_MEDIO) <= 0) {
            base = NivelRiesgo.MEDIO;
            reglaBase = "50000000 < monto <= 200000000";
        } else {
            base = NivelRiesgo.ALTO;
            reglaBase = "monto > 200000000";
        }

        List<String> motivos = new ArrayList<>();
        motivos.add("base " + base + " (" + reglaBase + ")");

        NivelRiesgo nivel = base;
        if (tipoOperacion.isSensible()) {
            nivel = NivelRiesgo.ALTO;
            motivos.add("tipo de operación sensible exige ALTO");
        }
        if (PERFIL_ALTO.equals(perfilSolicitante.getCodigo())) {
            nivel = NivelRiesgo.ALTO;
            motivos.add("perfil de riesgo del solicitante exige ALTO");
        }

        boolean requiereEvaluacionExterna = requiereEvaluacionExterna(monto);
        if (requiereEvaluacionExterna) {
            motivos.add("evaluación externa requerida por monto > 200000000");
        }

        String detalle = "Riesgo interno " + nivel
            + ". Política interna de demostración: "
            + String.join("; ", motivos) + ".";

        return new ResultadoRiesgo(
            nivel,
            OrigenRiesgo.INTERNO,
            requiereEvaluacionExterna,
            false,
            detalle
        );
    }

    /**
     * La evaluación externa depende solo del monto. Se expone para que las
     * consultas no dupliquen el umbral.
     */
    public static boolean requiereEvaluacionExterna(BigDecimal monto) {
        return monto != null && monto.compareTo(LIMITE_MEDIO) > 0;
    }
}
