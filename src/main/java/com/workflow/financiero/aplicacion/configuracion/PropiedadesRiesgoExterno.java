package com.workflow.financiero.aplicacion.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la integración asíncrona de riesgo. El secreto se toma del
 * entorno y nunca se muestra en logs ni en mensajes de error.
 */
@ConfigurationProperties(prefix = "flujo.riesgo-externo")
public record PropiedadesRiesgoExterno(
    String proveedor,
    String secreto,
    Duration ventanaTiempo,
    Duration reintento,
    int maximoIntentos
) {

    public PropiedadesRiesgoExterno {
        proveedor = proveedor == null || proveedor.isBlank()
            ? "proveedor-riesgo-simulado"
            : proveedor;
        secreto = secreto == null ? "" : secreto;
        if (ventanaTiempo == null || ventanaTiempo.isZero()
            || ventanaTiempo.isNegative()) {
            throw new IllegalStateException(
                "La ventana de la firma de riesgo debe ser mayor que cero");
        }
        if (reintento == null || reintento.isNegative()) {
            throw new IllegalStateException(
                "La espera de reintento de riesgo no puede ser negativa");
        }
        if (maximoIntentos <= 0) {
            throw new IllegalStateException(
                "El máximo de intentos de riesgo debe ser mayor que cero");
        }
    }

    @Override
    public String toString() {
        return "PropiedadesRiesgoExterno[proveedor=" + proveedor
            + ", secreto=***, ventanaTiempo=" + ventanaTiempo
            + ", reintento=" + reintento
            + ", maximoIntentos=" + maximoIntentos + "]";
    }
}
