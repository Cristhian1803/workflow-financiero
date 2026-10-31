package com.workflow.financiero.dominio.ejecucion;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Operación que se envía al proveedor financiero. La clave y la huella se
 * calculan con la identidad estable y los parámetros de la revisión.
 */
public record OperacionFinanciera(
    UUID solicitudId,
    UUID revisionId,
    AccionFinanciera accion,
    String clave,
    String huella,
    BigDecimal monto,
    String moneda,
    String tipoOperacion
) {

    private static final Pattern FORMATO_HUELLA =
        Pattern.compile("[0-9a-f]{64}");

    public OperacionFinanciera {
        if (solicitudId == null || revisionId == null || accion == null) {
            throw new IllegalArgumentException(
                "La solicitud, la revisión y la acción son obligatorias");
        }
        if (clave == null || clave.isBlank() || clave.length() > 100) {
            throw new IllegalArgumentException("La clave no es válida");
        }
        if (huella == null || !FORMATO_HUELLA.matcher(
                huella.trim().toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("La huella no es válida");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (moneda == null || moneda.trim().length() != 3) {
            throw new IllegalArgumentException("La moneda no es válida");
        }
        if (tipoOperacion == null || tipoOperacion.isBlank()
            || tipoOperacion.length() > 30) {
            throw new IllegalArgumentException(
                "El tipo de operación no es válido");
        }

        huella = huella.trim().toLowerCase(Locale.ROOT);
        moneda = moneda.trim().toUpperCase(Locale.ROOT);
        tipoOperacion = tipoOperacion.trim().toUpperCase(Locale.ROOT);
    }

    public static OperacionFinanciera crear(
        UUID solicitudId,
        UUID revisionId,
        AccionFinanciera accion,
        BigDecimal monto,
        String moneda,
        String tipoOperacion
    ) {
        String clave = ClavesOperacionFinanciera.clave(
            accion, solicitudId, revisionId);
        String huella = HuellaOperacionFinanciera.calcular(
            accion, solicitudId, revisionId, monto, moneda, tipoOperacion);
        return new OperacionFinanciera(
            solicitudId,
            revisionId,
            accion,
            clave,
            huella,
            monto,
            moneda,
            tipoOperacion
        );
    }
}
