package com.workflow.financiero.dominio.regla.aprobacion;

import java.util.List;

/**
 * Resultado de la verificación previa a la ejecución. Cuando no está
 * autorizado conserva todos los motivos encontrados.
 */
public record ResultadoAutorizacion(boolean autorizado, List<String> motivos) {

    public ResultadoAutorizacion {
        if (motivos == null) {
            throw new IllegalArgumentException("Los motivos son obligatorios");
        }
        motivos = List.copyOf(motivos);
        if (autorizado && !motivos.isEmpty()) {
            throw new IllegalArgumentException("Una autorización no puede tener motivos");
        }
        if (!autorizado && motivos.isEmpty()) {
            throw new IllegalArgumentException("Una negativa debe tener al menos un motivo");
        }
    }
}
