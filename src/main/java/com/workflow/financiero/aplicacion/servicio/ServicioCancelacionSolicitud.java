package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoCancelacionProceso;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancela una revisión antes de que exista un efecto financiero irreversible.
 * Cancela las tareas de negocio pendientes y deja constancia en el historial;
 * la cancelación real del proceso se delega en un puerto.
 */
@Service
@RequiredArgsConstructor
public class ServicioCancelacionSolicitud {

    private static final int LONGITUD_MAXIMA_MOTIVO = 1000;
    private static final String ROL_SOLICITANTE = "SOLICITANTE";
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioTransicionRevision servicioTransicion;
    private final PuertoCancelacionProceso puertoCancelacion;

    @Transactional
    public ResultadoCancelacionSolicitud cancelar(
        UUID solicitudId,
        UUID revisionId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        if (solicitudId == null || revisionId == null || actual == null) {
            throw new IllegalArgumentException(
                "La solicitud, la revisión y el actor son obligatorios");
        }
        String motivoNormalizado = validarMotivo(motivo);

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(SolicitudNoEncontradaException::new);

        boolean propietario = esPropietario(solicitud, actual);
        verificarAutorizacion(actual, propietario);

        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null || !revision.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (!puedeCancelarLocalmente(revision.getEstado())) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no se puede cancelar en estado "
                    + revision.getEstado());
        }

        Usuario actor = usuarioRepositorio.findById(actual.id())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el actor " + actual.id()));
        Rol rol = rolRepositorio.findById(
            propietario ? ROL_SOLICITANTE : ROL_ADMINISTRADOR
        ).orElse(null);

        // La solicitud ya está bloqueada; las tareas se bloquean en orden de
        // rol antes de modificar su estado.
        List<TareaAprobacion> tareas = tareaRepositorio
            .buscarPorRevisionParaActualizar(revision.getId());
        List<TareaAprobacion> pendientes = tareas.stream()
            .filter(tarea -> tarea.getEstado() == EstadoTareaAprobacion.PENDIENTE)
            .toList();

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.CANCELACION_SOLICITADA,
            actor,
            rol,
            revision.getEstado(),
            EstadoRevision.CANCELADA,
            correlacion,
            "Cancelación solicitada: " + motivoNormalizado
        );

        pendientes.forEach(TareaAprobacion::cancelar);
        if (!pendientes.isEmpty()) {
            tareaRepositorio.saveAllAndFlush(pendientes);
        }

        for (TareaAprobacion tarea : pendientes) {
            servicioEvento.registrar(
                solicitud,
                revision,
                TipoEventoSolicitud.TAREA_CANCELADA,
                actor,
                tarea.getRol(),
                null,
                null,
                correlacion,
                "Tarea de " + tarea.getRol().getCodigo()
                    + " cancelada por cancelación de la solicitud"
            );
        }

        servicioTransicion.transicionar(
            solicitudId,
            revision.getId(),
            EstadoRevision.CANCELADA,
            new ContextoTransicion(
                actor.getId(),
                rol == null ? null : rol.getCodigo(),
                correlacion,
                "Solicitud cancelada: " + motivoNormalizado
            )
        );

        if (revision.getIdentificadorProceso() != null
            && !revision.getIdentificadorProceso().isBlank()) {
            puertoCancelacion.solicitarCancelacion(
                revision.getIdentificadorProceso(),
                motivoNormalizado
            );
        }

        return new ResultadoCancelacionSolicitud(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.CANCELADA
        );
    }

    private void verificarAutorizacion(
        UsuarioActual actual,
        boolean propietario
    ) {
        if (propietario || actual.tieneRol(ROL_ADMINISTRADOR)) {
            return;
        }
        // Un solicitante sin visibilidad recibe 404; los demás roles, 403.
        if (actual.tieneRol(ROL_SOLICITANTE)) {
            throw new SolicitudNoEncontradaException();
        }
        throw new AccesoDenegadoException(
            "Solo el dueño o un administrador pueden cancelar la solicitud");
    }

    private boolean esPropietario(
        SolicitudFinanciera solicitud,
        UsuarioActual actual
    ) {
        return solicitud.getSolicitante().getId().equals(actual.id());
    }

    private boolean puedeCancelarLocalmente(EstadoRevision estado) {
        return switch (estado) {
            case RECIBIDA, VALIDANDO, EVALUANDO_RIESGO,
                PENDIENTE_APROBACION, APROBADA -> true;
            default -> false;
        };
    }

    private String validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new DecisionInvalidaException(
                "motivo",
                "El motivo de la cancelación es obligatorio"
            );
        }
        if (motivo.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new DecisionInvalidaException(
                "motivo",
                "El motivo no puede superar los "
                    + LONGITUD_MAXIMA_MOTIVO + " caracteres"
            );
        }
        return motivo.trim();
    }
}
