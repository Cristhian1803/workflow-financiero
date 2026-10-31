package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.modelo.EstadoNotificacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificación lista para exponerse al destinatario autenticado.
 */
public record NotificacionDetalle(
    UUID id,
    TipoNotificacion tipo,
    String contenido,
    EstadoNotificacion estado,
    UUID idTarea,
    UUID idSolicitud,
    Integer revision,
    Instant fechaCreacion,
    Instant fechaLectura
) {
}
