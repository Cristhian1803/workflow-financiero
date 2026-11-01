package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarea pendiente preparada para la bandeja del aprobador.
 */
public record TareaAprobacionBandeja(
    UUID id,
    UUID idSolicitud,
    UUID idRevision,
    int revision,
    String rol,
    EstadoTareaAprobacion estado,
    String asignadoA,
    Instant fechaCreacion,
    Instant fechaNotificacion,
    Instant fechaVencimiento,
    String solicitante,
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    NivelRiesgo nivelRiesgo,
    String descripcion,
    String justificacion
) {
}
