package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Datos completos de una solicitud y su revisión actual.
 */
@Schema(description = "Solicitud financiera con su revisión vigente")
public record SolicitudRespuesta(
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID id,

    @Schema(example = "0b3c4d5e-6f70-4a1b-8c2d-3e4f5a6b7c8d")
    UUID idRevision,

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

    @Schema(example = "Pago de demostración")
    String descripcion,

    @Schema(example = "Operación autorizada para pruebas")
    String justificacion,

    @Schema(example = "EVALUANDO_RIESGO")
    EstadoRevision estado,

    @Schema(example = "BAJO")
    NivelRiesgo nivelRiesgo,

    @Schema(description = "Roles funcionales que deberán aprobar",
        example = "[\"SUPERVISOR\"]")
    List<String> aprobacionesRequeridas,

    @Schema(description = "Verdadero cuando el monto supera 200.000.000",
        example = "false")
    boolean requiereEvaluacionExterna,

    @Schema(example = "2026-10-30T12:00:00Z")
    Instant fechaCreacion,

    @Schema(example = "2026-10-30T12:00:00Z")
    Instant fechaActualizacion
) {

    public SolicitudRespuesta {
        aprobacionesRequeridas = List.copyOf(aprobacionesRequeridas);
    }
}
