package com.workflow.financiero.aplicacion.servicio;

/**
 * Resultado de un reintento operativo solicitado desde la API.
 */
public record ResultadoReintentoIncidente(
    IncidenteDetalle incidente,
    ResultadoReconciliacion resultado
) {
}
