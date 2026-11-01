package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.NivelRiesgo;

/**
 * Datos validados de una resolución humana de riesgo.
 */
public record ResolverRevisionRiesgoComando(
    NivelRiesgo nivel,
    String motivo,
    String evidencia
) {
}
