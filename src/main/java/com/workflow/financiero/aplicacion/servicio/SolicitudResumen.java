package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Datos resumidos de una solicitud para el listado paginado.
 */
public record SolicitudResumen(
    UUID id,
    int revision,
    String solicitante,
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    EstadoRevision estado,
    NivelRiesgo nivelRiesgo,
    Instant fechaCreacion
) {
}
