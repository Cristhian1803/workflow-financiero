package com.workflow.financiero.infraestructura.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma y huella de las notificaciones de riesgo. La firma cubre las
 * cabeceras de correlación y los bytes exactos del cuerpo.
 */
public final class FirmaRiesgoExterno {

    private static final String ALGORITMO = "HmacSHA256";

    private FirmaRiesgoExterno() {
    }

    public static String canonico(
        String proveedor,
        String identificadorEvento,
        String timestamp,
        byte[] cuerpo
    ) {
        return proveedor + "\n"
            + identificadorEvento + "\n"
            + timestamp + "\n"
            + new String(cuerpo, StandardCharsets.UTF_8);
    }

    public static String firmar(
        String secreto,
        String proveedor,
        String identificadorEvento,
        String timestamp,
        byte[] cuerpo
    ) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(
                secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] firma = mac.doFinal(
                canonico(proveedor, identificadorEvento, timestamp, cuerpo)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(firma);
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException(
                "No se pudo calcular la firma del evento", excepcion);
        }
    }

    public static String huella(byte[] cuerpo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(cuerpo));
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException(
                "No se pudo calcular la huella del evento", excepcion);
        }
    }

    public static boolean firmaValida(String esperada, String recibida) {
        if (esperada == null || recibida == null) {
            return false;
        }
        return MessageDigest.isEqual(
            esperada.getBytes(StandardCharsets.UTF_8),
            recibida.getBytes(StandardCharsets.UTF_8)
        );
    }
}
