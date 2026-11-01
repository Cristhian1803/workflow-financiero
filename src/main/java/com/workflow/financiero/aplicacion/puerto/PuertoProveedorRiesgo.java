package com.workflow.financiero.aplicacion.puerto;

import com.workflow.financiero.aplicacion.servicio.SolicitudEvaluacionRiesgo;

/**
 * Contrato saliente con el proveedor de riesgo. La respuesta es una referencia
 * estable que permite auditar la solicitud enviada.
 */
public interface PuertoProveedorRiesgo {

    String solicitarEvaluacion(SolicitudEvaluacionRiesgo solicitud);
}
