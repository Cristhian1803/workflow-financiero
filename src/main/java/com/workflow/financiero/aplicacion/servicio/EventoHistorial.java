package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de auditoría listo para exponerse en el historial.
 */
public record EventoHistorial(
    UUID id,
    int revision,
    TipoEventoSolicitud tipo,
    String actor,
    String rol,
    EstadoRevision estadoAnterior,
    EstadoRevision estadoNuevo,
    String detalle,
    String identificadorCorrelacion,
    Instant fecha
) {
}
