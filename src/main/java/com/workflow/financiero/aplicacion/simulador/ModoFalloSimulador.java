package com.workflow.financiero.aplicacion.simulador;

/**
 * Fallos controlados del proveedor simulado. Solo se configuran en pruebas o
 * desde un componente de demostración.
 */
public enum ModoFalloSimulador {

    FALLO_ANTES_DEL_EFECTO,
    RESPUESTA_PERDIDA,
    CONSULTA_NO_DISPONIBLE,
    LIBERACION_FALLIDA
}
