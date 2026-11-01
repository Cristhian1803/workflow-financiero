package com.workflow.financiero.dominio.regla.validacion;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos de entrada que llegan a la validación antes de crear una solicitud.
 * El perfil del solicitante no forma parte de este contrato: se obtiene del
 * usuario persistido.
 */
public record SolicitudAValidar(
    UUID solicitanteId,
    String tipoOperacion,
    BigDecimal monto,
    String moneda,
    String descripcion,
    String justificacion
) {
}
