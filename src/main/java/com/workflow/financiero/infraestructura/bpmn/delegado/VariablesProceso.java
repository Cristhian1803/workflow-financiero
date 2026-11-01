package com.workflow.financiero.infraestructura.bpmn.delegado;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;

/**
 * Lectura y conversión de las variables que comparten los delegates.
 */
final class VariablesProceso {

    private VariablesProceso() {
    }

    static UUID uuid(DelegateExecution ejecucion, String nombre) {
        Object valor = ejecucion.getVariable(nombre);
        if (valor == null) {
            throw new IllegalStateException("Falta la variable " + nombre);
        }
        try {
            return UUID.fromString(valor.toString());
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalStateException(
                "La variable " + nombre + " no es un identificador válido",
                excepcion
            );
        }
    }

    static String texto(DelegateExecution ejecucion, String nombre) {
        Object valor = ejecucion.getVariable(nombre);
        return valor == null ? null : valor.toString();
    }

    static Boolean booleano(DelegateExecution ejecucion, String nombre) {
        Object valor = ejecucion.getVariable(nombre);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Boolean resultado) {
            return resultado;
        }
        return Boolean.parseBoolean(valor.toString());
    }

    static Collection<String> listaTexto(DelegateExecution ejecucion, String nombre) {
        Object valor = ejecucion.getVariable(nombre);
        if (valor == null) {
            return List.of();
        }
        if (valor instanceof Collection<?> coleccion) {
            return coleccion.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        }
        throw new IllegalStateException(
            "La variable " + nombre + " no es una lista");
    }
}
