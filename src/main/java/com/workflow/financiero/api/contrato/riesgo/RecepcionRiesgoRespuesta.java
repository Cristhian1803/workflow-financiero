package com.workflow.financiero.api.contrato.riesgo;

import com.workflow.financiero.dominio.estado.EstadoEventoExterno;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Acuse de recepción de una notificación de riesgo.
 */
@Schema(description = "Estado de procesamiento del evento externo")
public record RecepcionRiesgoRespuesta(
    @Schema(example = "evt-123")
    String identificadorEvento,

    @Schema(example = "PROCESADO")
    EstadoEventoExterno estado,

    @Schema(example = "2026-10-31T12:00:00Z")
    Instant recibidoEn
) {
}
