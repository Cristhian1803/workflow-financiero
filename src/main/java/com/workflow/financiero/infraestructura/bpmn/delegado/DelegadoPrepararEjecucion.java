package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioIntencionEjecucion;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoPrepararEjecucion")
@RequiredArgsConstructor
public class DelegadoPrepararEjecucion implements JavaDelegate {

    private final ServicioIntencionEjecucion servicioIntencion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioIntencion.preparar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            VariablesProceso.texto(ejecucion, "correlacion")
        );
    }
}
