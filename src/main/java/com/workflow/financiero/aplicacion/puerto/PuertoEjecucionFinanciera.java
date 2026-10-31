package com.workflow.financiero.aplicacion.puerto;

import java.util.UUID;

/**
 * Punto de salida para la ejecución controlada de una revisión. En esta etapa
 * no representa una operación financiera real.
 */
public interface PuertoEjecucionFinanciera {

    void preparar(UUID solicitudId, UUID revisionId);

    void registrarResultado(UUID solicitudId, UUID revisionId);
}
