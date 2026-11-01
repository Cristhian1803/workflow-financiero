package com.workflow.financiero.aplicacion.excepcion;

/**
 * Error definitivo del proveedor: validación rechazada o respuesta inválida.
 * No debe reintentarse automáticamente.
 */
public class ErrorDefinitivoProveedorFinancieroException extends RuntimeException {

    public ErrorDefinitivoProveedorFinancieroException(String mensaje) {
        super(mensaje);
    }

    public ErrorDefinitivoProveedorFinancieroException(
        String mensaje,
        Throwable causa
    ) {
        super(mensaje, causa);
    }
}
