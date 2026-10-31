package com.workflow.financiero.infraestructura.externo.contrato;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo HTTP del contrato del proveedor financiero.
 */
public record OperacionProveedorFinancieroPeticion(
    UUID solicitudId,
    UUID revisionId,
    AccionFinanciera accion,
    String clave,
    String huella,
    BigDecimal monto,
    String moneda,
    String tipoOperacion
) {

    public static OperacionProveedorFinancieroPeticion desde(
        OperacionFinanciera operacion
    ) {
        return new OperacionProveedorFinancieroPeticion(
            operacion.solicitudId(),
            operacion.revisionId(),
            operacion.accion(),
            operacion.clave(),
            operacion.huella(),
            operacion.monto(),
            operacion.moneda(),
            operacion.tipoOperacion()
        );
    }
}
