package com.workflow.financiero.dominio.ejecucion;

import java.util.UUID;

/**
 * Claves estables por solicitud, revisión y acción. No contienen secretos y
 * permiten que un reintento conserve la misma identidad.
 */
public final class ClavesOperacionFinanciera {

    private ClavesOperacionFinanciera() {
    }

    public static String reserva(UUID solicitudId, UUID revisionId) {
        return clave(AccionFinanciera.RESERVA, solicitudId, revisionId);
    }

    public static String confirmacion(UUID solicitudId, UUID revisionId) {
        return clave(AccionFinanciera.CONFIRMACION, solicitudId, revisionId);
    }

    public static String liberacion(UUID solicitudId, UUID revisionId) {
        return clave(AccionFinanciera.LIBERACION, solicitudId, revisionId);
    }

    public static String clave(
        AccionFinanciera accion,
        UUID solicitudId,
        UUID revisionId
    ) {
        if (accion == null || solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La acción, la solicitud y la revisión son obligatorias");
        }
        return accion.etiquetaClave() + ":" + solicitudId + ":" + revisionId;
    }
}
