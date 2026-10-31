package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoIntentoEjecucion;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos mínimos que necesita el trabajador para llamar al proveedor sin
 * mantener una transacción abierta.
 */
public record TrabajoEjecucion(
    UUID intentoId,
    UUID solicitudId,
    UUID revisionId,
    String procesoId,
    String clave,
    String huella,
    BigDecimal monto,
    String moneda,
    String tipoOperacion,
    EstadoIntentoEjecucion estado,
    int intentos
) {
}
