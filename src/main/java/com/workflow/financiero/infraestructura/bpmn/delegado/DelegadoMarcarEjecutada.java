package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioEventoSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoMarcarEjecutada")
@RequiredArgsConstructor
public class DelegadoMarcarEjecutada implements JavaDelegate {

    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioIncidente servicioIncidente;
    private final RevisionSolicitudRepositorio revisionRepositorio;

    @Override
    public void execute(DelegateExecution ejecucion) {
        UUID solicitudId = VariablesProceso.uuid(ejecucion, "solicitudId");
        UUID revisionId = VariablesProceso.uuid(ejecucion, "revisionId");
        String correlacion = VariablesProceso.texto(
            ejecucion, "correlacion");

        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(revisionId)
            .orElseThrow(() -> new IllegalStateException(
                "No existe la revisión " + revisionId));
        EstadoRevision anterior = revision.getEstado();
        if (anterior == EstadoRevision.EJECUTADA) {
            return;
        }

        RevisionSolicitud actualizada = servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.EJECUTADA,
            new ContextoTransicion(
                null,
                null,
                correlacion,
                "Ejecución confirmada por el proveedor"
            )
        );

        servicioEvento.registrar(
            actualizada.getSolicitud(),
            actualizada,
            TipoEventoSolicitud.EJECUCION_CONFIRMADA,
            null,
            null,
            anterior,
            EstadoRevision.EJECUTADA,
            correlacion,
            "Resultado de ejecución confirmado"
        );

        servicioIncidente.resolverPorRevision(
            revisionId,
            null,
            "Resultado de ejecución confirmado",
            correlacion
        );
    }
}
