package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoDecision;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de aprobar o rechazar una tarea, con el estado final de la tarea y
 * de la revisión.
 */
public record ResultadoDecisionAprobacion(
    UUID idTarea,
    TipoDecision decision,
    Instant fecha,
    EstadoTareaAprobacion estadoTarea,
    EstadoRevision estadoRevision,
    boolean delegado
) {
}
