package com.workflow.financiero.dominio.regla.validacion;

/**
 * Valida que la justificación no esté vacía y no supere la longitud permitida.
 */
public class ValidadorJustificacion implements ValidadorSolicitud {

    private static final int LONGITUD_MAXIMA = 1000;

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        String justificacion = contexto.justificacion();
        if (justificacion == null || justificacion.isBlank()) {
            return ResultadoValidacion.conError(
                "justificacion", "La justificación es obligatoria");
        }
        if (justificacion.length() > LONGITUD_MAXIMA) {
            return ResultadoValidacion.conError(
                "justificacion", "La justificación no puede superar los " + LONGITUD_MAXIMA
                    + " caracteres");
        }
        return ResultadoValidacion.valido();
    }
}
