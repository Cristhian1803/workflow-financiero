package com.workflow.financiero.aplicacion.puerto;

import java.time.Instant;
import java.util.List;

/**
 * Representación mínima de una tarea activa del motor.
 */
public record TareaMotor(
    String id,
    String definicion,
    String nombre,
    String asignadoA,
    List<String> gruposCandidatos,
    Instant creadaEn
) {

    public TareaMotor {
        gruposCandidatos = List.copyOf(gruposCandidatos);
    }
}
