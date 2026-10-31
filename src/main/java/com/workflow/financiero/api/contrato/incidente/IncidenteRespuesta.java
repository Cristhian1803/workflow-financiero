package com.workflow.financiero.api.contrato.incidente;

import com.workflow.financiero.dominio.estado.EstadoIncidente;
import com.workflow.financiero.dominio.estado.TipoIncidente;
import java.time.Instant;
import java.util.UUID;

/**
 * Incidente operativo visible para operaciones y administración.
 */
public record IncidenteRespuesta(
    UUID id,
    UUID idSolicitud,
    UUID idRevision,
    TipoIncidente tipo,
    UUID referencia,
    EstadoIncidente estado,
    String motivo,
    String actor,
    boolean reintentable,
    int reintentos,
    String ultimoResultado,
    Instant fechaCreacion,
    Instant fechaActualizacion
) {
}
