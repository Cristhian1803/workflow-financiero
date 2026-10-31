package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioEventoSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoMarcarEjecutada")
@RequiredArgsConstructor
public class DelegadoMarcarEjecutada implements JavaDelegate {

    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;

    @Override
    public void execute(DelegateExecution ejecucion) {
        String correlacion = VariablesProceso.texto(
            ejecucion, "correlacion");
        RevisionSolicitud revision = servicioTransicion.transicionar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId"),
            EstadoRevision.EJECUTADA,
            new ContextoTransicion(
                null,
                null,
                correlacion,
                "Ejecución confirmada por el proveedor"
            )
        );

        servicioEvento.registrar(
            revision.getSolicitud(),
            revision,
            TipoEventoSolicitud.EJECUCION_CONFIRMADA,
            null,
            null,
            EstadoRevision.EJECUTANDO,
            EstadoRevision.EJECUTADA,
            correlacion,
            "Resultado de ejecución confirmado"
        );
    }
}
