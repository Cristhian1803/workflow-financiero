package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.Notificacion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import com.workflow.financiero.infraestructura.persistencia.NotificacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera la notificación de escalamiento cuando una tarea sigue pendiente
 * pasado el plazo configurado. La operación es idempotente y no notifica
 * tareas resueltas ni tareas que ya tienen un responsable asignado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioNotificacionEscalamiento {

    private final TareaAprobacionRepositorio tareaRepositorio;
    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final NotificacionRepositorio notificacionRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final PropiedadesAprobacion propiedadesAprobacion;

    @Transactional
    public boolean notificarEscalamiento(
        String idTareaMotor,
        String correlacion
    ) {
        if (idTareaMotor == null || idTareaMotor.isBlank()) {
            return false;
        }

        UUID solicitudId = tareaRepositorio
            .buscarSolicitudIdPorIdentificadorTareaMotor(idTareaMotor)
            .orElse(null);
        if (solicitudId == null) {
            log.info(
                "No se notifica la tarea de motor {}: la tarea de negocio no existe",
                idTareaMotor
            );
            return false;
        }

        // La solicitud se bloquea antes que la tarea para que una aprobación
        // o cancelación concurrente no deje una notificación tardía.
        if (solicitudRepositorio.buscarPorIdParaActualizar(solicitudId).isEmpty()) {
            return false;
        }

        TareaAprobacion tarea = tareaRepositorio
            .buscarPorIdentificadorTareaMotorParaActualizar(idTareaMotor)
            .orElse(null);
        if (tarea == null) {
            log.info(
                "No se notifica la tarea de motor {}: la tarea de negocio no existe",
                idTareaMotor
            );
            return false;
        }
        if (tarea.getEstado() != EstadoTareaAprobacion.PENDIENTE) {
            return false;
        }
        // Una tarea con asignado ya fue reasignada; no corresponde una
        // notificación tardía de 24 horas.
        if (tarea.getAsignado() != null) {
            return false;
        }
        if (!esRevisionActual(tarea)
            || tarea.getRevision().getEstado() != EstadoRevision.PENDIENTE_APROBACION) {
            return false;
        }

        String rolCodigo = tarea.getRol().getCodigo();
        if (notificacionRepositorio.existePorTareaTipoYRol(
            tarea.getId(),
            TipoNotificacion.ESCALAMIENTO_24H,
            rolCodigo
        )) {
            return false;
        }

        Notificacion notificacion = new Notificacion(
            tarea,
            null,
            tarea.getRol(),
            TipoNotificacion.ESCALAMIENTO_24H,
            contenido(tarea, propiedadesAprobacion.notificacion())
        );
        notificacionRepositorio.saveAndFlush(notificacion);

        servicioEvento.registrar(
            tarea.getRevision().getSolicitud(),
            tarea.getRevision(),
            TipoEventoSolicitud.NOTIFICACION_ESCALAMIENTO,
            null,
            tarea.getRol(),
            null,
            null,
            correlacion,
            "Notificación de escalamiento para la tarea de " + rolCodigo
        );

        return true;
    }

    private boolean esRevisionActual(TareaAprobacion tarea) {
        RevisionSolicitud actual = tarea.getRevision()
            .getSolicitud()
            .getRevisionActual();
        return actual != null
            && actual.getId().equals(tarea.getRevision().getId());
    }

    private String contenido(TareaAprobacion tarea, Duration duracion) {
        return "La tarea de aprobación de " + tarea.getRol().getCodigo()
            + " lleva " + TextoDuracion.describir(duracion) + " pendiente";
    }
}
