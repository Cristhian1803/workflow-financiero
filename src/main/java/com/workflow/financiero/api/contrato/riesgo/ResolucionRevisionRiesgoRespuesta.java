package com.workflow.financiero.api.contrato.riesgo;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de resolver una tarea de revisión manual.
 */
@Schema(description = "Resultado de la revisión manual de riesgo")
public record ResolucionRevisionRiesgoRespuesta(
    UUID idTarea,
    EstadoTareaRevisionRiesgo estadoTarea,
    EstadoRevision estadoRevision,
    NivelRiesgo nivelRiesgo,
    Instant fecha
) {
}
