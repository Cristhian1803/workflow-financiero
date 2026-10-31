package com.workflow.financiero.infraestructura.bpmn;

import com.workflow.financiero.aplicacion.puerto.PuertoCancelacionProceso;
import org.springframework.stereotype.Component;

/**
 * Adaptador provisional mientras la cancelación real del proceso no está
 * conectada. Falla de forma explícita para no simular una cancelación que el
 * motor no ejecutó.
 */
@Component
public class AdaptadorCancelacionProcesoNoConectado
    implements PuertoCancelacionProceso {

    @Override
    public void solicitarCancelacion(String idProceso, String motivo) {
        throw new UnsupportedOperationException(
            "La cancelación del proceso todavía no está conectada al motor");
    }
}
