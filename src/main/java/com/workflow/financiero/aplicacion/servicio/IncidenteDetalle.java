package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoIncidente;
import com.workflow.financiero.dominio.estado.TipoIncidente;
import java.time.Instant;
import java.util.UUID;

/**
 * Vista de aplicación de un incidente, sin exponer las entidades JPA.
 */
public record IncidenteDetalle(
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
