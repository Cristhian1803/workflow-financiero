package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento del historial de una solicitud. No se exponen rutas de modificación
 * ni de borrado.
 */
@Schema(description = "Evento inmutable del historial")
public record EventoHistorialRespuesta(
    @Schema(example = "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d")
    UUID id,

    @Schema(example = "1")
    int revision,

    @Schema(example = "SOLICITUD_CREADA")
    TipoEventoSolicitud tipo,

    @Schema(example = "demo")
    String actor,

    @Schema(example = "SOLICITANTE")
    String rol,

    @Schema(example = "RECIBIDA")
    EstadoRevision estadoAnterior,

    @Schema(example = "VALIDANDO")
    EstadoRevision estadoNuevo,

    @Schema(example = "Solicitud registrada")
    String detalle,

    @Schema(example = "corr-manual-123")
    String identificadorCorrelacion,

    @Schema(example = "2026-10-30T12:00:00Z")
    Instant fecha
) {
}
