package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
import java.util.List;

/**
 * Resultado de preparar una revisión: el estado vigente, el riesgo calculado y
 * los roles que deben aprobar.
 */
public record PreparacionRevision(
    RevisionSolicitud revision,
    ResultadoRiesgo riesgo,
    List<String> roles
) {

    public PreparacionRevision {
        roles = List.copyOf(roles);
    }
}
