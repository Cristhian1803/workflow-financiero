package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioVerificacionProceso;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoVerificarSolicitud")
@RequiredArgsConstructor
public class DelegadoVerificarSolicitud implements JavaDelegate {

    private final ServicioVerificacionProceso servicioVerificacion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioVerificacion.verificarSolicitud(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId")
        );
    }
}
