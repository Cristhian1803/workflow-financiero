package com.workflow.financiero.aplicacion.servicio;

import java.time.Instant;
import java.util.UUID;

/**
 * Contenido validado de una notificación externa de riesgo.
 */
public record NotificacionRiesgoComando(
    String proveedor,
    String identificadorEvento,
    UUID identificadorSolicitud,
    int numeroRevision,
    String nivelRiesgo,
    Instant fechaProveedor,
    String resultado
) {
}
