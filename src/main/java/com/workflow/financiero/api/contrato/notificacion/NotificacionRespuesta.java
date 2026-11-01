package com.workflow.financiero.api.contrato.notificacion;

import com.workflow.financiero.dominio.modelo.EstadoNotificacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificación visible para el actor autenticado.
 */
@Schema(description = "Notificación persistida para el actor o sus roles")
public record NotificacionRespuesta(
    @Schema(example = "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d")
    UUID id,

    @Schema(example = "ESCALAMIENTO_24H")
    TipoNotificacion tipo,

    @Schema(example = "La tarea de aprobación de SUPERVISOR lleva 24 horas pendiente")
    String contenido,

    @Schema(example = "PENDIENTE")
    EstadoNotificacion estado,

    @Schema(description = "Tarea relacionada, si existe", nullable = true)
    UUID idTarea,

    @Schema(description = "Solicitud relacionada, si existe", nullable = true)
    UUID idSolicitud,

    @Schema(description = "Número de revisión, si existe", nullable = true)
    Integer revision,

    @Schema(example = "2026-10-31T12:00:00Z")
    Instant fechaCreacion,

    @Schema(description = "Fecha de lectura, todavía no implementada", nullable = true)
    Instant fechaLectura
) {
}
