package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia el estado de la revisión actual y registra el evento en la misma
 * transacción. Siempre bloquea primero la solicitud y después la revisión.
 */
@Service
@RequiredArgsConstructor
public class ServicioTransicionRevision {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final ServicioEventoSolicitud servicioEvento;

    @Transactional
    public RevisionSolicitud transicionar(
        UUID solicitudId,
        UUID revisionId,
        EstadoRevision nuevoEstado,
        ContextoTransicion contexto
    ) {
        if (contexto == null) {
            throw new IllegalArgumentException("El contexto de transición es obligatorio");
        }

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));

        RevisionSolicitud revisionActual = solicitud.getRevisionActual();
        if (revisionActual == null || !revisionActual.getId().equals(revision.getId())) {
            throw new TransicionEstadoInvalidaException(
                "La revisión " + revisionId + " no es la actual de la solicitud " + solicitudId);
        }

        Usuario actor = resolverActor(contexto.actorId());
        Rol rol = resolverRol(contexto.rolCodigo());

        if (revision.getEstado() == EstadoRevision.REVISION_MANUAL
            && nuevoEstado == EstadoRevision.EVALUANDO_RIESGO
            && !contexto.revisionManualDeRiesgo()) {
            throw new TransicionEstadoInvalidaException(
                "El retorno desde revisión manual requiere un resultado de riesgo vigente");
        }

        EstadoRevision estadoAnterior = revision.getEstado();
        revision.cambiarEstado(nuevoEstado);

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.CAMBIO_ESTADO,
            actor,
            rol,
            estadoAnterior,
            nuevoEstado,
            contexto.correlacion(),
            contexto.detalle()
        );

        return revision;
    }

    private Usuario resolverActor(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return usuarioRepositorio.findById(actorId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el actor " + actorId));
    }

    private Rol resolverRol(String rolCodigo) {
        if (rolCodigo == null || rolCodigo.isBlank()) {
            return null;
        }
        return rolRepositorio.findById(rolCodigo)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el rol " + rolCodigo));
    }
}
