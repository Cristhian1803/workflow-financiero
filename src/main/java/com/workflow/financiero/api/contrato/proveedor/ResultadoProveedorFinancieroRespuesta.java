package com.workflow.financiero.api.contrato.proveedor;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado idempotente expuesto por el simulador.
 */
public record ResultadoProveedorFinancieroRespuesta(
    UUID id,
    String clave,
    AccionFinanciera accion,
    EstadoOperacionFinanciera estado,
    String referencia,
    String huella,
    Instant actualizadoEn
) {

    public static ResultadoProveedorFinancieroRespuesta desde(
        ResultadoOperacionFinanciera resultado
    ) {
        return new ResultadoProveedorFinancieroRespuesta(
            resultado.id(),
            resultado.clave(),
            resultado.accion(),
            resultado.estado(),
            resultado.referencia(),
            resultado.huella(),
            resultado.actualizadoEn()
        );
    }
}
