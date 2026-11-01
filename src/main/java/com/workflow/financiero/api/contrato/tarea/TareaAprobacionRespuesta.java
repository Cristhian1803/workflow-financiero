package com.workflow.financiero.api.contrato.tarea;

import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarea pendiente visible en la bandeja del aprobador.
 */
@Schema(description = "Tarea de aprobación pendiente visible para el actor")
public record TareaAprobacionRespuesta(
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID id,

    @Schema(example = "8a7f6e5d-4c3b-2a1f-0e9d-8c7b6a5f4e3d")
    UUID idSolicitud,

    @Schema(example = "7b6a5f4e-3d2c-1b0a-9f8e-7d6c5b4a3f2e")
    UUID idRevision,

    @Schema(example = "1")
    int revision,

    @Schema(example = "SUPERVISOR")
    String rol,

    @Schema(example = "PENDIENTE")
    EstadoTareaAprobacion estado,

    @Schema(description = "Usuario asignado, si existe", nullable = true)
    String asignadoA,

    @Schema(example = "2026-10-30T18:00:00Z")
    Instant fechaCreacion,

    @Schema(example = "2026-10-31T18:00:00Z")
    Instant fechaNotificacion,

    @Schema(example = "2026-11-01T18:00:00Z")
    Instant fechaVencimiento,

    @Schema(example = "demo")
    String solicitante,

    @Schema(example = "NORMAL")
    String tipoOperacion,

    @Schema(example = "25000000")
    BigDecimal monto,

    @Schema(example = "PYG")
    String moneda,

    @Schema(example = "BAJO")
    NivelRiesgo nivelRiesgo,

    @Schema(example = "Pago de servicios")
    String descripcion,

    @Schema(example = "Operación autorizada para pruebas")
    String justificacion
) {
}
