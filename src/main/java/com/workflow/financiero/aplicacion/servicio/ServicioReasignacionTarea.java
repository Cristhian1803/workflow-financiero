package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.Notificacion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.NotificacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reasigna una tarea pendiente al superior configurado para su rol funcional.
 * La tarea conserva el rol original y no se completa: solo cambia el asignado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioReasignacionTarea {

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final TareaAprobacionRepositorio tareaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final NotificacionRepositorio notificacionRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final PropiedadesAprobacion propiedadesAprobacion;
    private final PuertoProceso puertoProceso;

    @Transactional
    public boolean reasignar(String idTareaMotor, String correlacion) {
        if (idTareaMotor == null || idTareaMotor.isBlank()) {
            return false;
        }

        TareaAprobacion tarea = tareaRepositorio
            .buscarPorIdentificadorTareaMotor(idTareaMotor)
            .orElse(null);
        if (tarea == null) {
            log.info(
                "No se reasigna la tarea de motor {}: la tarea de negocio no existe",
                idTareaMotor
            );
            return false;
        }
        if (tarea.getEstado() != EstadoTareaAprobacion.PENDIENTE) {
            return false;
        }
        // Una tarea ya asignada no se vuelve a reasignar, aunque el job se
        // reintente o el temporizador se ejecute de nuevo.
        if (tarea.getAsignado() != null) {
            return false;
        }
        if (!esRevisionActual(tarea)) {
            log.info(
                "No se reasigna la tarea {}: la revisión ya no es la actual",
                idTareaMotor
            );
            return false;
        }

        String rolFuncional = tarea.getRol().getCodigo();
        String rolSuperior = propiedadesAprobacion.superiores().get(rolFuncional);
        if (rolSuperior == null || rolSuperior.isBlank()) {
            registrarFallo(tarea, rolFuncional, null, correlacion);
            return false;
        }

        List<Usuario> superiores =
            usuarioRepositorio.buscarActivosPorRol(rolSuperior);
        Usuario superior = superiores.isEmpty() ? null : superiores.get(0);
        if (superior == null) {
            registrarFallo(tarea, rolFuncional, rolSuperior, correlacion);
            return false;
        }

        tarea.reasignar(superior);
        // Se fuerza la verificación de versión antes de tocar el motor para
        // que una aprobación concurrente no quede pisada.
        tareaRepositorio.saveAndFlush(tarea);
        puertoProceso.asignarTarea(idTareaMotor, superior.getUsuario());

        if (!notificacionRepositorio.existePorTareaTipoYUsuario(
            tarea.getId(),
            TipoNotificacion.REASIGNACION_48H,
            superior.getId()
        )) {
            notificacionRepositorio.saveAndFlush(new Notificacion(
                tarea,
                superior,
                null,
                TipoNotificacion.REASIGNACION_48H,
                contenidoReasignacion(tarea, superior)
            ));
        }

        servicioEvento.registrar(
            tarea.getRevision().getSolicitud(),
            tarea.getRevision(),
            TipoEventoSolicitud.TAREA_REASIGNADA,
            null,
            tarea.getRol(),
            null,
            null,
            correlacion,
            "Tarea de " + rolFuncional + " reasignada de sin asignar a "
                + superior.getUsuario()
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

    private void registrarFallo(
        TareaAprobacion tarea,
        String rolFuncional,
        String rolSuperior,
        String correlacion
    ) {
        Rol administrador = rolRepositorio.findById(ROL_ADMINISTRADOR)
            .filter(Rol::isActivo)
            .orElse(null);

        if (administrador != null
            && notificacionRepositorio.existePorTareaTipoYRol(
                tarea.getId(),
                TipoNotificacion.REASIGNACION_FALLIDA,
                administrador.getCodigo()
            )) {
            return;
        }

        String detalle = detalleFallo(rolFuncional, rolSuperior);
        if (administrador != null) {
            notificacionRepositorio.saveAndFlush(new Notificacion(
                tarea,
                null,
                administrador,
                TipoNotificacion.REASIGNACION_FALLIDA,
                detalle
            ));
        } else {
            log.error(
                "No existe el rol {} para registrar el fallo de reasignación",
                ROL_ADMINISTRADOR
            );
        }

        servicioEvento.registrar(
            tarea.getRevision().getSolicitud(),
            tarea.getRevision(),
            TipoEventoSolicitud.REASIGNACION_FALLIDA,
            null,
            tarea.getRol(),
            null,
            null,
            correlacion,
            detalle
        );
    }

    private String contenidoReasignacion(
        TareaAprobacion tarea,
        Usuario superior
    ) {
        return "La tarea de aprobación de " + tarea.getRol().getCodigo()
            + " fue reasignada a " + superior.getUsuario() + " tras "
            + TextoDuracion.describir(propiedadesAprobacion.reasignacion())
            + " pendiente";
    }

    private String detalleFallo(String rolFuncional, String rolSuperior) {
        if (rolSuperior == null || rolSuperior.isBlank()) {
            return "No hay un superior configurado para reasignar la tarea de "
                + rolFuncional;
        }
        return "No hay un usuario activo con el rol " + rolSuperior
            + " para reasignar la tarea de " + rolFuncional;
    }
}
