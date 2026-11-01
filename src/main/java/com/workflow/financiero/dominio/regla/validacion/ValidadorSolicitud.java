package com.workflow.financiero.dominio.regla.validacion;

/**
 * Regla de validación independiente que puede combinarse con otras.
 */
public interface ValidadorSolicitud {

    ResultadoValidacion validar(ContextoValidacion contexto);
}
