package com.workflow.financiero.aplicacion.servicio;

/**
 * Resultado de comprobar si un actor puede ver y resolver una tarea. La
 * visibilidad se separa de la elegibilidad para no revelar tareas ajenas.
 */
public record ElegibilidadAprobacion(
    boolean visible,
    boolean elegible,
    boolean delegado,
    String rolFuncional,
    String motivo
) {
}
