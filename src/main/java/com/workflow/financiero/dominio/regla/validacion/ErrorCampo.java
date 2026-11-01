package com.workflow.financiero.dominio.regla.validacion;

/**
 * Error de validación asociado a un campo de la solicitud.
 */
public record ErrorCampo(String campo, String mensaje) {

    public ErrorCampo {
        if (campo == null || campo.isBlank()) {
            throw new IllegalArgumentException("El campo del error es obligatorio");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El mensaje del error es obligatorio");
        }
    }
}
