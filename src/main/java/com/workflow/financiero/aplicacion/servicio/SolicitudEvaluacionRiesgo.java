package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos que recibe el proveedor de riesgo para evaluar una revisión. La
 * correlación es estable y no cambia entre reintentos.
 */
public record SolicitudEvaluacionRiesgo(
    UUID idEvaluacion,
    UUID idSolicitud,
    UUID idRevision,
    int numeroRevision,
    NivelRiesgo nivelInterno,
    BigDecimal monto,
    String moneda,
    String tipoOperacion,
    String correlacion
) {
}
