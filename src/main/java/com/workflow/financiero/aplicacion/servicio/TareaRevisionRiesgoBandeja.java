package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarea pendiente de revisión manual preparada para la bandeja de Riesgos.
 */
public record TareaRevisionRiesgoBandeja(
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
