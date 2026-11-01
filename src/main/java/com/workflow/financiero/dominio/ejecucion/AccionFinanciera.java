package com.workflow.financiero.dominio.ejecucion;

/**
 * Acciones del contrato con el proveedor financiero. Cada acción tiene una
 * clave estable distinta para la misma revisión.
 */
public enum AccionFinanciera {

    RESERVA,
    CONFIRMACION,
    LIBERACION;

    public String etiquetaClave() {
        return switch (this) {
            case RESERVA -> "reserva";
            case CONFIRMACION -> "confirmacion";
            case LIBERACION -> "liberacion";
        };
    }
}
