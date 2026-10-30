package com.workflow.financiero.aplicacion.servicio;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Punto de verificación del riesgo externo. Hasta que exista una integración
 * real, el proceso no puede avanzar por esta ruta.
 */
@Service
public class ServicioRiesgoExternoProceso {

    public void verificar(UUID solicitudId, UUID revisionId) {
        throw new IllegalStateException(
            "La evaluación externa de riesgo todavía no está habilitada");
    }
}
