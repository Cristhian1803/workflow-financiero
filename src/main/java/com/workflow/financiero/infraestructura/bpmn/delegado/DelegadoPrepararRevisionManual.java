package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioEventoSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaRevisionRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.infraestructura.persistencia.EvaluacionRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaRevisionRiesgoRepositorio;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Prepara la revisión manual cuando vence la espera externa. La tarea de
 * negocio se crea después, cuando Flowable crea la userTask.
 */
@Component("delegadoPrepararRevisionManual")
@RequiredArgsConstructor
public class DelegadoPrepararRevisionManual implements JavaDelegate {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final EvaluacionRiesgoRepositorio evaluacionRepositorio;
    private final TareaRevisionRiesgoRepositorio tareaRevisionRepositorio;
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;

    @Override
    public void execute(DelegateExecution ejecucion) {
        UUID solicitudId = VariablesProceso.uuid(ejecucion, "solicitudId");
        UUID revisionId = VariablesProceso.uuid(ejecucion, "revisionId");
        String correlacion = VariablesProceso.texto(ejecucion, "correlacion");

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElse(null);
        if (solicitud == null) {
            ejecucion.setVariable("revisionManualRequerida", false);
            return;
        }
        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(revisionId)
            .orElse(null);
        if (revision == null
            || solicitud.getRevisionActual() == null
            || !solicitud.getRevisionActual().getId().equals(revisionId)) {
            ejecucion.setVariable("revisionManualRequerida", false);
            return;
        }

        if (revision.getEstado() == EstadoRevision.REVISION_MANUAL) {
            TareaRevisionRiesgo existente = tareaRevisionRepositorio
                .findByRevisionId(revisionId)
                .orElse(null);
            ejecucion.setVariable(
                "revisionManualRequerida",
                existente == null
            );
            return;
        }
        if (revision.getEstado() != EstadoRevision.PENDIENTE_APROBACION) {
            ejecucion.setVariable("revisionManualRequerida", false);
            return;
        }
        if (evaluacionRepositorio.buscarVigentePorRevisionParaActualizar(
                revisionId,
                OrigenRiesgo.EXTERNO,
                List.of(EstadoEvaluacionRiesgo.RECIBIDA))
            .isPresent()) {
            ejecucion.setVariable("revisionManualRequerida", false);
            return;
        }

        evaluacionRepositorio.buscarVigentePorRevisionParaActualizar(
                revisionId,
                OrigenRiesgo.EXTERNO,
                List.of(EstadoEvaluacionRiesgo.PENDIENTE))
            .ifPresent(evaluacion -> {
                evaluacion.marcarVencida(
                    "La evaluación externa no llegó dentro del plazo");
                evaluacionRepositorio.flush();
            });

        RevisionSolicitud manual = servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.REVISION_MANUAL,
            new ContextoTransicion(
                null,
                null,
                correlacion,
                "Vencimiento de la espera de riesgo externo"
            )
        );
        servicioEvento.registrar(
            solicitud,
            manual,
            TipoEventoSolicitud.EVALUACION_EXTERNA_VENCIDA,
            null,
            null,
            EstadoRevision.PENDIENTE_APROBACION,
            EstadoRevision.REVISION_MANUAL,
            correlacion,
            "La evaluación externa venció y requiere revisión manual"
        );
        ejecucion.setVariable("revisionManualRequerida", true);
    }
}
