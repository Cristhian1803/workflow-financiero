package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.EvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.infraestructura.persistencia.EvaluacionRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaRevisionRiesgoRepositorio;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida, dentro del delegate BPMN, que el resultado externo ya esté
 * persistido y corresponda a la revisión que está esperando. No aplica el
 * resultado ni crea eventos: eso ocurre en la transacción del inbox.
 */
@Service
@RequiredArgsConstructor
public class ServicioRiesgoExternoProceso {

    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final EvaluacionRiesgoRepositorio evaluacionRepositorio;
    private final TareaRevisionRiesgoRepositorio tareaRevisionRepositorio;

    @Transactional(readOnly = true)
    public void verificar(
        UUID revisionId,
        String identificadorEvento,
        String correlacion
    ) {
        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));
        SolicitudFinanciera solicitud = revision.getSolicitud();

        if (solicitud.getRevisionActual() == null
            || !solicitud.getRevisionActual().getId().equals(revision.getId())) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (revision.getEstado() != EstadoRevision.PENDIENTE_APROBACION) {
            throw new TransicionEstadoInvalidaException(
                "No se puede resolver el riesgo externo en estado "
                    + revision.getEstado());
        }
        if (revision.getNivelRiesgo() == null) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no tiene el riesgo interno clasificado");
        }

        EvaluacionRiesgo evaluacion = identificadorEvento == null
            ? evaluacionManual(revision.getId())
            : evaluacionExterna(revision.getId(), identificadorEvento);
        if (evaluacion.getNivel() == null) {
            throw new TransicionEstadoInvalidaException(
                "La evaluación de riesgo no tiene un nivel válido");
        }
        if (evaluacion.getNivel().compareTo(revision.getNivelRiesgo()) > 0) {
            throw new TransicionEstadoInvalidaException(
                "El riesgo consolidado es menor que la evaluación aplicada");
        }
    }

    private EvaluacionRiesgo evaluacionExterna(
        UUID revisionId,
        String identificadorEvento
    ) {
        EvaluacionRiesgo evaluacion = evaluacionRepositorio
            .buscarVigentePorRevision(
                revisionId,
                OrigenRiesgo.EXTERNO,
                List.of(EstadoEvaluacionRiesgo.RECIBIDA))
            .orElseThrow(() -> new TransicionEstadoInvalidaException(
                "No existe una evaluación externa recibida para la revisión"));
        if (!identificadorEvento.equals(evaluacion.getIdentificadorEvento())) {
            throw new TransicionEstadoInvalidaException(
                "El evento no corresponde a la evaluación externa aplicada");
        }
        return evaluacion;
    }

    private EvaluacionRiesgo evaluacionManual(UUID revisionId) {
        if (!tareaRevisionRepositorio.existsByRevisionIdAndEstado(
                revisionId, EstadoTareaRevisionRiesgo.RESUELTA)) {
            throw new TransicionEstadoInvalidaException(
                "No existe una tarea de revisión manual resuelta");
        }
        return evaluacionRepositorio
            .buscarVigentePorRevision(
                revisionId,
                OrigenRiesgo.MANUAL,
                List.of(EstadoEvaluacionRiesgo.MANUAL))
            .orElseThrow(() -> new TransicionEstadoInvalidaException(
                "No existe una evaluación manual aplicada para la revisión"));
    }
}
