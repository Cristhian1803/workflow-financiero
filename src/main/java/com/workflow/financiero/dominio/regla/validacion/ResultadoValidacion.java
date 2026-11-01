package com.workflow.financiero.dominio.regla.validacion;

import java.util.List;

/**
 * Errores acumulados de una validación de solicitud.
 */
public record ResultadoValidacion(List<ErrorCampo> errores) {

    public ResultadoValidacion {
        errores = List.copyOf(errores);
    }

    public static ResultadoValidacion valido() {
        return new ResultadoValidacion(List.of());
    }

    public static ResultadoValidacion conError(String campo, String mensaje) {
        return new ResultadoValidacion(List.of(new ErrorCampo(campo, mensaje)));
    }

    public boolean esValido() {
        return errores.isEmpty();
    }
}
