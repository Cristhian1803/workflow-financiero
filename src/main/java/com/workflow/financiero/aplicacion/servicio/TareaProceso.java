package com.workflow.financiero.aplicacion.servicio;

import java.time.Instant;

/**
 * Tarea activa del proceso preparada para la respuesta HTTP.
 */
public record TareaProceso(
    String id,
    String definicion,
    String nombre,
    String estado,
    String asignadoA,
    String rol,
    Instant fechaVencimiento
) {
}
