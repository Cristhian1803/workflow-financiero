package com.workflow.financiero.aplicacion.excepcion;

/**
 * Error transitorio del proveedor: error de servidor, timeout o fallo de
 * conexión. La política de reintentos pertenece al trabajador de ejecución.
 */
public class ErrorRecuperableProveedorFinancieroException extends RuntimeException {

    public ErrorRecuperableProveedorFinancieroException(String mensaje) {
        super(mensaje);
    }

    public ErrorRecuperableProveedorFinancieroException(
        String mensaje,
        Throwable causa
    ) {
        super(mensaje, causa);
    }
}
