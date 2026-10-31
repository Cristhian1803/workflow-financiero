package com.workflow.financiero.aplicacion.servicio;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de guardar un evento en el inbox antes de correlacionarlo.
 */
public record RegistroEventoExterno(
    UUID idEvento,
    String identificadorEvento,
    boolean nuevo,
    Instant recibidoEn
) {
}
