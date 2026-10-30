package com.workflow.financiero.aplicacion.servicio;

import java.math.BigDecimal;

/**
 * Datos de entrada para registrar una solicitud. No incluye identidad,
 * estado, riesgo ni roles: todo eso lo determina el servidor.
 */
public record CrearSolicitudComando(
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    String descripcion,
    String justificacion
) {
}
