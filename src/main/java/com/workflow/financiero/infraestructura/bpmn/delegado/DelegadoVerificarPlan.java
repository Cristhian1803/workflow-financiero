package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioVerificacionProceso;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoVerificarPlan")
@RequiredArgsConstructor
public class DelegadoVerificarPlan implements JavaDelegate {

    private final ServicioVerificacionProceso servicioVerificacion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioVerificacion.verificarPlan(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            VariablesProceso.listaTexto(ejecucion, "rolesPlan")
        );
    }
}
