package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de resolver una tarea de revisión manual.
 */
public record ResultadoResolucionRevisionRiesgo(
    UUID idTarea,
    EstadoTareaRevisionRiesgo estadoTarea,
    EstadoRevision estadoRevision,
    NivelRiesgo nivelRiesgo,
    Instant fecha
) {
}
