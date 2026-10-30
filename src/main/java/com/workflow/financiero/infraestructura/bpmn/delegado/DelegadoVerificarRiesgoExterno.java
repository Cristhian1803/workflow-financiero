package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioRiesgoExternoProceso;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoVerificarRiesgoExterno")
@RequiredArgsConstructor
public class DelegadoVerificarRiesgoExterno implements JavaDelegate {

    private final ServicioRiesgoExternoProceso servicioRiesgoExterno;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioRiesgoExterno.verificar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId")
        );
    }
}
