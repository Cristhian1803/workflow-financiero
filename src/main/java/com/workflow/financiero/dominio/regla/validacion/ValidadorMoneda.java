package com.workflow.financiero.dominio.regla.validacion;

/**
 * Valida que la moneda exista, esté activa y sea PYG.
 */
public class ValidadorMoneda implements ValidadorSolicitud {

    private static final String MONEDA_ACEPTADA = "PYG";

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        if (contexto.monedaCodigo() == null || contexto.monedaCodigo().isBlank()) {
            return ResultadoValidacion.conError("moneda", "La moneda es obligatoria");
        }
        if (contexto.moneda() == null) {
            return ResultadoValidacion.conError("moneda", "La moneda no existe");
        }
        if (!contexto.moneda().isActiva()) {
            return ResultadoValidacion.conError("moneda", "La moneda no está activa");
        }
        if (!MONEDA_ACEPTADA.equals(contexto.moneda().getCodigo())) {
            return ResultadoValidacion.conError(
                "moneda", "Solo se acepta la moneda " + MONEDA_ACEPTADA);
        }
        return ResultadoValidacion.valido();
    }
}
