package com.workflow.financiero.dominio.regla.validacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ejecuta una lista ordenada de validadores y acumula todos sus errores.
 */
public class ValidadorSolicitudCompuesto implements ValidadorSolicitud {

    private final List<ValidadorSolicitud> validadores;

    public ValidadorSolicitudCompuesto(List<ValidadorSolicitud> validadores) {
        if (validadores == null || validadores.isEmpty()) {
            throw new IllegalArgumentException("La lista de validadores no puede estar vacía");
        }
        this.validadores = List.copyOf(validadores);
    }

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        Objects.requireNonNull(contexto, "El contexto de validación es obligatorio");

        List<ErrorCampo> errores = new ArrayList<>();
        for (ValidadorSolicitud validador : validadores) {
            errores.addAll(validador.validar(contexto).errores());
        }
        return new ResultadoValidacion(errores);
    }
}
