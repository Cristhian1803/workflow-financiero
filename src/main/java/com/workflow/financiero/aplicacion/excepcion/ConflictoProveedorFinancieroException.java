package com.workflow.financiero.aplicacion.excepcion;

/**
 * Conflicto definitivo del proveedor: la misma clave se usó con otros
 * parámetros o la operación no admite el estado solicitado.
 */
public class ConflictoProveedorFinancieroException extends RuntimeException {

    public ConflictoProveedorFinancieroException(String mensaje) {
        super(mensaje);
    }

    public ConflictoProveedorFinancieroException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
