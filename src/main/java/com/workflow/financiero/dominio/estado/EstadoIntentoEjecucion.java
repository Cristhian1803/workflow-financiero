package com.workflow.financiero.dominio.estado;

/**
 * Estados durables de la intención de ejecución financiera.
 */
public enum EstadoIntentoEjecucion {
    PENDIENTE,
    ENVIANDO,
    CONFIRMADA,
    RESULTADO_DESCONOCIDO,
    FALLIDA,
    COMPENSANDO,
    COMPENSADA
}
