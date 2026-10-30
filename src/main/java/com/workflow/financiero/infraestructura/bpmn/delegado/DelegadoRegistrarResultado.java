package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioPreparacionEjecucion;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoRegistrarResultado")
@RequiredArgsConstructor
public class DelegadoRegistrarResultado implements JavaDelegate {

    private final ServicioPreparacionEjecucion servicioPreparacion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioPreparacion.registrarResultado(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            VariablesProceso.texto(ejecucion, "correlacion")
        );
    }
}
