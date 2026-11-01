package com.workflow.financiero.dominio.riesgo;

import java.util.UUID;

/**
 * Clave estable de la solicitud de evaluación externa. No contiene secretos.
 */
public final class ClavesRiesgoExterno {

    private ClavesRiesgoExterno() {
    }

    public static String solicitud(UUID solicitudId, UUID revisionId) {
        return "riesgo:" + solicitudId + ":" + revisionId;
    }

    public static String manual(UUID solicitudId, UUID revisionId) {
        return "manual:" + solicitudId + ":" + revisionId;
    }
}
