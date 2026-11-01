package com.workflow.financiero.aplicacion.configuracion;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del adaptador HTTP del proveedor financiero. El secreto se
 * toma del entorno y nunca se muestra en logs ni en mensajes de error.
 */
@ConfigurationProperties(prefix = "integracion.proveedor-financiero")
public record PropiedadesProveedorFinanciero(
    URI urlBase,
    Duration tiempoConexion,
    Duration tiempoLectura,
    String secreto
) {

    public PropiedadesProveedorFinanciero {
        if (urlBase == null) {
            throw new IllegalStateException(
                "La URL base del proveedor financiero es obligatoria");
        }
        if (tiempoConexion == null || tiempoConexion.isZero()
            || tiempoConexion.isNegative()) {
            throw new IllegalStateException(
                "El tiempo de conexión del proveedor debe ser mayor que cero");
        }
        if (tiempoLectura == null || tiempoLectura.isZero()
            || tiempoLectura.isNegative()) {
            throw new IllegalStateException(
                "El tiempo de lectura del proveedor debe ser mayor que cero");
        }
        secreto = secreto == null ? "" : secreto;
    }

    @Override
    public String toString() {
        return "PropiedadesProveedorFinanciero[urlBase=" + urlBase
            + ", tiempoConexion=" + tiempoConexion
            + ", tiempoLectura=" + tiempoLectura
            + ", secreto=***]";
    }
}
