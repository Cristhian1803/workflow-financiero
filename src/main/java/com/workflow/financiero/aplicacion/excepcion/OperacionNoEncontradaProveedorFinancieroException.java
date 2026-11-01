package com.workflow.financiero.aplicacion.excepcion;

/**
 * El proveedor no conoce la clave consultada. En reconciliación esto significa
 * que la acción todavía no produjo efecto, no que el proveedor esté caído.
 */
public class OperacionNoEncontradaProveedorFinancieroException
    extends ErrorDefinitivoProveedorFinancieroException {

    public OperacionNoEncontradaProveedorFinancieroException(String mensaje) {
        super(mensaje);
    }

    public OperacionNoEncontradaProveedorFinancieroException(
        String mensaje,
        Throwable causa
    ) {
        super(mensaje, causa);
    }
}
