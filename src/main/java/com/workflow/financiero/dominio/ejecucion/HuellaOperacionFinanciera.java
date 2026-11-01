package com.workflow.financiero.dominio.ejecucion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Huella canónica de los parámetros que identifican una operación. El
 * proveedor la usa para distinguir un reintento idéntico de un conflicto.
 */
public final class HuellaOperacionFinanciera {

    private static final char SEPARADOR = '\u001f';

    private HuellaOperacionFinanciera() {
    }

    public static String calcular(OperacionFinanciera operacion) {
        if (operacion == null) {
            throw new IllegalArgumentException("La operación es obligatoria");
        }
        return calcular(
            operacion.accion(),
            operacion.solicitudId(),
            operacion.revisionId(),
            operacion.monto(),
            operacion.moneda(),
            operacion.tipoOperacion()
        );
    }

    public static String calcular(
        AccionFinanciera accion,
        UUID solicitudId,
        UUID revisionId,
        BigDecimal monto,
        String moneda,
        String tipoOperacion
    ) {
        if (accion == null || solicitudId == null || revisionId == null
            || monto == null || moneda == null || tipoOperacion == null) {
            throw new IllegalArgumentException(
                "Los parámetros de la huella son obligatorios");
        }

        String canonico = String.join(
            String.valueOf(SEPARADOR),
            solicitudId.toString(),
            revisionId.toString(),
            accion.name(),
            monto.toPlainString(),
            moneda.trim().toUpperCase(Locale.ROOT),
            tipoOperacion.trim().toUpperCase(Locale.ROOT)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible", error);
        }
    }
}
