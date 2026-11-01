package com.workflow.financiero.api.contrato.riesgo;

import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarea pendiente de revisión manual visible para Riesgos.
 */
@Schema(description = "Tarea humana de revisión de riesgo")
public record TareaRevisionRiesgoRespuesta(
    UUID id,
    UUID idSolicitud,
    UUID idRevision,
    int revision,
    EstadoTareaRevisionRiesgo estado,
    String solicitante,
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    NivelRiesgo nivelRiesgo,
    String descripcion,
    String justificacion,
    Instant fechaCreacion
) {
}
