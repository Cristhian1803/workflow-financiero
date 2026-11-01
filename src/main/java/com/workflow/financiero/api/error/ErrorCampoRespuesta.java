package com.workflow.financiero.api.error;

/**
 * Error de validación listo para el cuerpo de la respuesta.
 */
public record ErrorCampoRespuesta(String campo, String mensaje) {
}
