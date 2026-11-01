package com.workflow.financiero.aplicacion.servicio;

/**
 * Resultado de un intento de reconciliación con el proveedor.
 */
public enum ResultadoReconciliacion {
    RESUELTO,
    PENDIENTE,
    FALLIDO,
    NO_REINTENTABLE
}
