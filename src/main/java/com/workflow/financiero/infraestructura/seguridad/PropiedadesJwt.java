package com.workflow.financiero.infraestructura.seguridad;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del token JWT. El secreto se toma del entorno y nunca se
 * muestra en logs ni en mensajes de error.
 */
@ConfigurationProperties(prefix = "seguridad.jwt")
public record PropiedadesJwt(String secreto, String emisor, long expiracionMinutos) {

    private static final int BYTES_MINIMOS_SECRETO = 32;

    public PropiedadesJwt {
        if (secreto == null || secreto.isBlank()
            || secreto.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS_SECRETO) {
            throw new IllegalStateException(
                "JWT_SECRETO debe estar definido y tener al menos 32 bytes");
        }
        if (emisor == null || emisor.isBlank()) {
            throw new IllegalStateException("JWT_EMISOR no puede estar vacío");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException(
                "JWT_EXPIRACION_MINUTOS debe ser mayor que cero");
        }
    }

    @Override
    public String toString() {
        return "PropiedadesJwt[secreto=***, emisor=" + emisor
            + ", expiracionMinutos=" + expiracionMinutos + "]";
    }
}
