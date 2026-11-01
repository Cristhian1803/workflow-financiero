package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista completa de una solicitud y su revisión actual.
 */
public record SolicitudDetalle(
    UUID id,
    UUID idRevision,
    int revision,
    String solicitante,
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    String descripcion,
    String justificacion,
    EstadoRevision estado,
    NivelRiesgo nivelRiesgo,
    List<String> aprobacionesRequeridas,
    boolean requiereEvaluacionExterna,
    Instant fechaCreacion,
    Instant fechaActualizacion
) {

    public SolicitudDetalle {
        aprobacionesRequeridas = List.copyOf(aprobacionesRequeridas);
    }
}
