package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el rechazo de la revisión actual, cancela las demás tareas de
 * negocio y deja el historial en la misma transacción del motor.
 */
@Service
@RequiredArgsConstructor
public class ServicioRechazoProceso {

    private final ServicioTransicionRevision servicioTransicion;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ServicioEventoSolicitud servicioEvento;

    @Transactional
    public void registrar(
        UUID solicitudId,
        UUID revisionId,
        UUID tareaAprobacionId,
        UUID actorId,
        String rolFuncional,
        String motivo,
        String correlacion
    ) {
        Usuario actor = actorId == null
            ? null
            : usuarioRepositorio.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException(
                    "No existe el actor " + actorId));

        List<TareaAprobacion> canceladas = tareaRepositorio
            .buscarPorRevision(revisionId)
            .stream()
            .filter(tarea -> tarea.getEstado() == EstadoTareaAprobacion.PENDIENTE)
            .filter(tarea -> tareaAprobacionId == null
                || !tarea.getId().equals(tareaAprobacionId))
            .toList();

        canceladas.forEach(TareaAprobacion::cancelar);
        if (!canceladas.isEmpty()) {
            tareaRepositorio.saveAllAndFlush(canceladas);
        }

        for (TareaAprobacion tarea : canceladas) {
            servicioEvento.registrar(
                tarea.getRevision().getSolicitud(),
                tarea.getRevision(),
                TipoEventoSolicitud.TAREA_CANCELADA,
                actor,
                tarea.getRol(),
                null,
                null,
                correlacion,
                "Tarea de " + tarea.getRol().getCodigo()
                    + " cancelada por rechazo"
            );
        }

        String detalle = motivo == null || motivo.isBlank()
            ? "Rechazo registrado por el proceso"
            : motivo;

        servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.RECHAZADA,
            new ContextoTransicion(actorId, rolFuncional, correlacion, detalle)
        );
        tareaRepositorio.flush();
    }
}
