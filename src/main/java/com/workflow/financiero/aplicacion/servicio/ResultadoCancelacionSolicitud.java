package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.UUID;

/**
 * Resultado de una cancelación local: identifica la revisión afectada y el
 * estado en que quedó.
 */
public record ResultadoCancelacionSolicitud(
    UUID idSolicitud,
    UUID idRevision,
    EstadoRevision estadoRevision
) {
}
