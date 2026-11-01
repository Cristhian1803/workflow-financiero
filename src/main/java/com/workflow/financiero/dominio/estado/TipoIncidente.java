package com.workflow.financiero.dominio.estado;

/**
 * Origen del incidente operativo. En esta etapa solo se generan incidentes de
 * ejecución; los otros tipos quedan previstos para las etapas siguientes.
 */
public enum TipoIncidente {
    EJECUCION,
    COMPENSACION,
    RIESGO_EXTERNO
}
