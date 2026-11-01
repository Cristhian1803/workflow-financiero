package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoMarcarAprobada")
@RequiredArgsConstructor
public class DelegadoMarcarAprobada implements JavaDelegate {

    private final ServicioTransicionRevision servicioTransicion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        servicioTransicion.transicionar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            EstadoRevision.APROBADA,
            new ContextoTransicion(
                null,
                null,
                VariablesProceso.texto(ejecucion, "correlacion"),
                "Solicitud aprobada"
            )
        );
    }
}
