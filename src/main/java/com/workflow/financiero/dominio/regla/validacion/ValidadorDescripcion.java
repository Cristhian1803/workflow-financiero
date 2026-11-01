package com.workflow.financiero.dominio.regla.validacion;

/**
 * Valida que la descripción no esté vacía y no supere la longitud permitida.
 */
public class ValidadorDescripcion implements ValidadorSolicitud {

    private static final int LONGITUD_MAXIMA = 500;

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        String descripcion = contexto.descripcion();
        if (descripcion == null || descripcion.isBlank()) {
            return ResultadoValidacion.conError(
                "descripcion", "La descripción es obligatoria");
        }
        if (descripcion.length() > LONGITUD_MAXIMA) {
            return ResultadoValidacion.conError(
                "descripcion", "La descripción no puede superar los " + LONGITUD_MAXIMA
                    + " caracteres");
        }
        return ResultadoValidacion.valido();
    }
}
