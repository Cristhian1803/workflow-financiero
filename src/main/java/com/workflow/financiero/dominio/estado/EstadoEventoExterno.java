package com.workflow.financiero.dominio.estado;

/**
 * Estado de procesamiento de un evento recibido en el inbox externo.
 */
public enum EstadoEventoExterno {
    PENDIENTE,
    PROCESADO,
    DUPLICADO,
    CONFLICTO,
    OBSOLETO
}
