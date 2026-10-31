package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioEventoSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.infraestructura.persistencia.IntentoEjecucionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoMarcarResultadoDesconocido")
@RequiredArgsConstructor
public class DelegadoMarcarResultadoDesconocido implements JavaDelegate {

    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioIncidente servicioIncidente;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final IntentoEjecucionRepositorio intentoRepositorio;

    @Override
    public void execute(DelegateExecution ejecucion) {
        UUID solicitudId = VariablesProceso.uuid(ejecucion, "solicitudId");
        UUID revisionId = VariablesProceso.uuid(ejecucion, "revisionId");
        String correlacion = VariablesProceso.texto(
            ejecucion, "correlacion");
        String motivo = VariablesProceso.texto(ejecucion, "motivo");
        if (motivo == null || motivo.isBlank()) {
            motivo = "El proveedor no confirmó el resultado de la ejecución";
        }

        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(revisionId)
            .orElseThrow(() -> new IllegalStateException(
                "No existe la revisión " + revisionId));
        EstadoRevision anterior = revision.getEstado();
        if (anterior == EstadoRevision.RESULTADO_DESCONOCIDO
            || anterior == EstadoRevision.EJECUTADA) {
            return;
        }

        RevisionSolicitud actualizada = servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.RESULTADO_DESCONOCIDO,
            new ContextoTransicion(
                null,
                null,
                correlacion,
                motivo
            )
        );

        servicioEvento.registrar(
            actualizada.getSolicitud(),
            actualizada,
            TipoEventoSolicitud.EJECUCION_RESULTADO_DESCONOCIDO,
            null,
            null,
            anterior,
            EstadoRevision.RESULTADO_DESCONOCIDO,
            correlacion,
            motivo
        );

        IntentoEjecucion intento = intentoRepositorio
            .buscarPorRevisionId(revisionId)
            .orElse(null);
        if (intento != null) {
            servicioIncidente.crearEjecucion(
                actualizada.getSolicitud(),
                actualizada,
                intento.getId(),
                motivo,
                true,
                correlacion
            );
        }
    }
}
