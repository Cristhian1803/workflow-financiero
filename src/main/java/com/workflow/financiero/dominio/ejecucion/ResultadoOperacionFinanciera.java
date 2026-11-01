package com.workflow.financiero.dominio.ejecucion;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado idempotente devuelto por el proveedor para una operación.
 */
public record ResultadoOperacionFinanciera(
    UUID id,
    String clave,
    AccionFinanciera accion,
    EstadoOperacionFinanciera estado,
    String referencia,
    String huella,
    Instant actualizadoEn
) {
}
