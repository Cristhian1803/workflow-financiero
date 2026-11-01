package com.workflow.financiero.aplicacion.puerto;

/**
 * Solicita al motor de procesos la cancelación de una instancia. La aplicación
 * no conoce los detalles de Flowable.
 */
public interface PuertoCancelacionProceso {

    void solicitarCancelacion(String idProceso, String motivo);
}
