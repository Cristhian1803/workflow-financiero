package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.Notificacion;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import com.workflow.financiero.infraestructura.persistencia.NotificacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.time.Duration;
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

        TareaAprobacion tarea = tareaRepositorio
            .buscarPorIdentificadorTareaMotor(idTareaMotor)
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

    private String contenido(TareaAprobacion tarea, Duration duracion) {
        return "La tarea de aprobación de " + tarea.getRol().getCodigo()
            + " lleva " + describirDuracion(duracion) + " pendiente";
    }

    private String describirDuracion(Duration duracion) {
        if (duracion.toHours() > 0 && duracion.toMinutesPart() == 0) {
            long horas = duracion.toHours();
            return horas + (horas == 1 ? " hora" : " horas");
        }
        if (duracion.toMinutes() > 0 && duracion.toSecondsPart() == 0) {
            long minutos = duracion.toMinutes();
            return minutos + (minutos == 1 ? " minuto" : " minutos");
        }

        long segundos = duracion.toSeconds();
        return segundos + (segundos == 1 ? " segundo" : " segundos");
    }
}
