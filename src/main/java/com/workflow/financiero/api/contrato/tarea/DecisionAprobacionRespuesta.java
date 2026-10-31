package com.workflow.financiero.api.contrato.tarea;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de aprobar o rechazar una tarea de aprobación.
 */
@Schema(description = "Decisión registrada sobre una tarea")
public record DecisionAprobacionRespuesta(
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID idTarea,

    @Schema(example = "APROBADA")
    TipoDecision decision,

    @Schema(example = "2026-10-30T18:00:00Z")
    Instant fecha,

    @Schema(example = "APROBADA")
    EstadoTareaAprobacion estadoTarea,

    @Schema(example = "PENDIENTE_APROBACION")
    EstadoRevision estadoRevision,

    @Schema(example = "false")
    boolean delegado
) {
}
