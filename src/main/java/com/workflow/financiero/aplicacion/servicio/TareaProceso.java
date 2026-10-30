package com.workflow.financiero.aplicacion.servicio;

/**
 * Tarea que se mostrará cuando exista el proceso BPMN. En esta etapa la lista
 * siempre está vacía.
 */
public record TareaProceso(
    String id,
    String nombre,
    String estado,
    String asignadoA
) {
}
