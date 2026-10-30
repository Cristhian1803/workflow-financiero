package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioRechazoProceso;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoRegistrarRechazo")
@RequiredArgsConstructor
public class DelegadoRegistrarRechazo implements JavaDelegate {

    private final ServicioRechazoProceso servicioRechazo;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioRechazo.registrar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            VariablesProceso.texto(ejecucion, "motivo"),
            VariablesProceso.texto(ejecucion, "correlacion")
        );
    }
}
