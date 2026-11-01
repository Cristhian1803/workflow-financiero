package com.workflow.financiero.dominio.regla.riesgo;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.util.Objects;

/**
 * Combina el riesgo interno con un resultado externo ya recibido. Un externo
 * ausente o inválido se representa con {@code null} y obliga a revisión
 * manual; la integración asíncrona no forma parte de esta clase.
 */
public class ConsolidadorRiesgo {

    public ResultadoRiesgo consolidar(ResultadoRiesgo interno, NivelRiesgo externo) {
        Objects.requireNonNull(interno, "El resultado interno es obligatorio");

        if (!interno.requiereEvaluacionExterna() || interno.requiereRevisionManual()) {
            return interno;
        }

        if (externo == null) {
            return new ResultadoRiesgo(
                interno.nivel(),
                OrigenRiesgo.INTERNO,
                true,
                true,
                interno.detalle() + " Evaluación externa ausente o inválida: no autoriza."
            );
        }

        NivelRiesgo nivel = interno.nivel().mayor(externo);
        // Si el externo empata con el interno, el nivel se atribuye al interno.
        OrigenRiesgo origen = externo.compareTo(interno.nivel()) > 0
            ? OrigenRiesgo.EXTERNO
            : OrigenRiesgo.INTERNO;

        String detalle = "Riesgo consolidado " + nivel
            + " (interno " + interno.nivel() + ", externo " + externo + ").";

        return new ResultadoRiesgo(nivel, origen, true, false, detalle);
    }
}
