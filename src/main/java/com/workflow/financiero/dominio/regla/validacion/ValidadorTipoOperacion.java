package com.workflow.financiero.dominio.regla.validacion;

/**
 * Valida que el tipo de operación exista y esté activo en el catálogo.
 */
public class ValidadorTipoOperacion implements ValidadorSolicitud {

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        if (contexto.tipoOperacionCodigo() == null || contexto.tipoOperacionCodigo().isBlank()) {
            return ResultadoValidacion.conError(
                "tipoOperacion", "El tipo de operación es obligatorio");
        }
        if (contexto.tipoOperacion() == null) {
            return ResultadoValidacion.conError(
                "tipoOperacion", "El tipo de operación no existe");
        }
        if (!contexto.tipoOperacion().isActivo()) {
            return ResultadoValidacion.conError(
                "tipoOperacion", "El tipo de operación no está activo");
        }
        return ResultadoValidacion.valido();
    }
}
