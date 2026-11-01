package com.workflow.financiero.infraestructura.externo;

import com.workflow.financiero.aplicacion.puerto.PuertoProveedorRiesgo;
import com.workflow.financiero.aplicacion.servicio.SolicitudEvaluacionRiesgo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulador en proceso del proveedor de riesgo. No envía notificaciones por sí
 * solo: la respuesta asíncrona se representa llamando al endpoint de
 * notificación. La referencia es estable para la misma correlación.
 */
@Component
@Slf4j
public class ProveedorRiesgoSimulado implements PuertoProveedorRiesgo {

    @Override
    public String solicitarEvaluacion(SolicitudEvaluacionRiesgo solicitud) {
        log.info(
            "Solicitud de riesgo externo enviada: correlación {}, revisión {}",
            solicitud.correlacion(),
            solicitud.idRevision()
        );
        return "SOL-" + solicitud.correlacion();
    }
}
