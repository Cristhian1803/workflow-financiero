package com.workflow.financiero.infraestructura.externo.contrato;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta HTTP del contrato del proveedor financiero.
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

    public ResultadoOperacionFinanciera aDominio() {
        return new ResultadoOperacionFinanciera(
            id,
            clave,
            accion,
            estado,
            referencia,
            huella,
            actualizadoEn
        );
    }
}
