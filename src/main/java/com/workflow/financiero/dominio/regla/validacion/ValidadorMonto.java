package com.workflow.financiero.dominio.regla.validacion;

import java.math.BigDecimal;

/**
 * Valida que el monto sea positivo, entero y compatible con NUMERIC(19,0).
 */
public class ValidadorMonto implements ValidadorSolicitud {

    private static final int DIGITOS_ENTEROS_MAXIMOS = 19;

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        BigDecimal monto = contexto.monto();
        if (monto == null) {
            return ResultadoValidacion.conError("monto", "El monto es obligatorio");
        }
        if (monto.signum() <= 0) {
            return ResultadoValidacion.conError("monto", "El monto debe ser mayor que cero");
        }

        // Se compara la parte fraccionaria real, no la escala: 1.0 es entero.
        BigDecimal entero = monto.stripTrailingZeros();
        if (entero.scale() > 0) {
            return ResultadoValidacion.conError(
                "monto", "El monto debe ser un número entero de guaraníes");
        }

        int digitos = entero.precision() - entero.scale();
        if (digitos > DIGITOS_ENTEROS_MAXIMOS) {
            return ResultadoValidacion.conError(
                "monto", "El monto no puede superar los " + DIGITOS_ENTEROS_MAXIMOS
                    + " dígitos");
        }
        return ResultadoValidacion.valido();
    }
}
