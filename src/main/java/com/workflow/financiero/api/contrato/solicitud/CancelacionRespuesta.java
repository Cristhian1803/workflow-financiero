package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.UUID;

/**
 * Estado resultante de la cancelación. Si la compensación sigue pendiente, el
 * cliente recibe 202 y puede consultar la solicitud.
 */
public record CancelacionRespuesta(
    UUID idSolicitud,
    UUID idRevision,
    EstadoRevision estadoRevision,
    boolean compensacionPendiente
) {
}
