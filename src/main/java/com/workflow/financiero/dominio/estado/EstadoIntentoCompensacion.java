package com.workflow.financiero.dominio.estado;

/**
 * Ciclo de vida durable de la compensación de una revisión. No se declara
 * CANCELADA hasta que la liberación queda confirmada o se demuestra que no
 * hubo efecto.
 */
public enum EstadoIntentoCompensacion {
    PENDIENTE,
    LIBERANDO,
    LIBERADA,
    CANCELADA,
    RECHAZADA,
    FALLIDA,
    REVISION_MANUAL
}
