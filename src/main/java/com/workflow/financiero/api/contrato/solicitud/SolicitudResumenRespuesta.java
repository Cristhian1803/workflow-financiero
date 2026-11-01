package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila del listado paginado de solicitudes.
 */
@Schema(description = "Resumen de una solicitud para el listado")
public record SolicitudResumenRespuesta(
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID id,

    @Schema(example = "1")
    int revision,

    @Schema(example = "demo")
    String solicitante,

    @Schema(example = "NORMAL")
    String tipoOperacion,

    @Schema(example = "25000000")
    BigDecimal monto,

    @Schema(example = "PYG")
    String moneda,

    @Schema(example = "EVALUANDO_RIESGO")
    EstadoRevision estado,

    @Schema(example = "BAJO")
    NivelRiesgo nivelRiesgo,

    @Schema(example = "2026-10-30T12:00:00Z")
    Instant fechaCreacion
) {
}
