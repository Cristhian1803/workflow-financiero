package com.workflow.financiero.aplicacion.servicio;

/**
 * Resultado seguro de una pasada de compensación. Solo LIBERADA y
 * CANCELADA_SIN_RESERVA permiten declarar la revisión como cancelada.
 */
public enum ResultadoCompensacion {
    LIBERADA,
    CANCELADA_SIN_RESERVA,
    RECHAZADA,
    PENDIENTE,
    FALLIDA,
    REVISION_MANUAL
}
