package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoIncidente;
import com.workflow.financiero.dominio.estado.TipoIncidente;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.IncidenteNoEncontradoException;
import com.workflow.financiero.dominio.excepcion.ReintentoNoPermitidoException;
import com.workflow.financiero.dominio.modelo.Incidente;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.IncidenteRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea, consulta y resuelve incidentes operativos. Los eventos de la solicitud
 * conservan el actor y el motivo de cada intervención.
 */
@Service
@RequiredArgsConstructor
public class ServicioIncidente {

    private static final List<EstadoIncidente> ESTADOS_VISIBLES = List.of(
        EstadoIncidente.ABIERTO,
        EstadoIncidente.REINTENTANDO
    );

    private final IncidenteRepositorio incidenteRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ServicioEventoSolicitud servicioEvento;

    @Transactional
    public Incidente crearEjecucion(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        UUID referencia,
        String motivo,
        boolean reintentable,
        String correlacion
    ) {
        Optional<Incidente> existente = incidenteRepositorio
            .findByTipoAndReferencia(TipoIncidente.EJECUCION, referencia);
        if (existente.isPresent()) {
            Incidente incidente = existente.get();
            incidente.actualizarMotivo(motivo);
            if (incidente.getEstado() == EstadoIncidente.RESUELTO
                || incidente.getEstado() == EstadoIncidente.CERRADO) {
                incidente.reabrir(motivo);
            }
            if (!reintentable) {
                incidente.marcarNoReintentable(motivo);
            }
            return incidente;
        }

        Incidente incidente = new Incidente(
            solicitud,
            revision,
            TipoIncidente.EJECUCION,
            referencia,
            motivo,
            reintentable
        );
        incidenteRepositorio.saveAndFlush(incidente);
        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.INCIDENTE_CREADO,
            null,
            null,
            revision.getEstado(),
            revision.getEstado(),
            correlacion,
            "Incidente de ejecución creado"
        );
        return incidente;
    }

    @Transactional
    public Incidente crearCompensacion(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        UUID referencia,
        String motivo,
        boolean reintentable,
        String correlacion
    ) {
        Optional<Incidente> existente = incidenteRepositorio
            .findByTipoAndReferencia(TipoIncidente.COMPENSACION, referencia);
        if (existente.isPresent()) {
            Incidente incidente = existente.get();
            incidente.actualizarMotivo(motivo);
            if (incidente.getEstado() == EstadoIncidente.RESUELTO
                || incidente.getEstado() == EstadoIncidente.CERRADO) {
                incidente.reabrir(motivo);
            }
            if (!reintentable) {
                incidente.marcarNoReintentable(motivo);
            }
            return incidente;
        }

        Incidente incidente = new Incidente(
            solicitud,
            revision,
            TipoIncidente.COMPENSACION,
            referencia,
            motivo,
            reintentable
        );
        incidenteRepositorio.saveAndFlush(incidente);
        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.INCIDENTE_CREADO,
            null,
            null,
            revision.getEstado(),
            revision.getEstado(),
            correlacion,
            "Incidente de compensación creado"
        );
        return incidente;
    }

    @Transactional(readOnly = true)
    public List<IncidenteDetalle> listarAbiertos(UsuarioActual actual) {
        exigirOperador(actual);
        return incidenteRepositorio.listarPorEstados(ESTADOS_VISIBLES)
            .stream()
            .map(this::detalle)
            .toList();
    }

    @Transactional(readOnly = true)
    public IncidenteDetalle obtenerParaReintento(
        UUID id,
        UsuarioActual actual
    ) {
        exigirOperador(actual);
        return detalle(buscarParaReintento(id));
    }

    @Transactional(readOnly = true)
    public IncidenteDetalle obtener(UUID id) {
        return detalle(incidenteRepositorio.findById(id)
            .orElseThrow(() -> new IncidenteNoEncontradoException(
                "No existe el incidente " + id)));
    }

    @Transactional
    public IncidenteDetalle marcarReintentando(
        UUID id,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        exigirOperador(actual);
        Incidente incidente = buscarParaReintentoBloqueado(id);
        Usuario actor = usuarioRepositorio.findById(actual.id())
            .orElseThrow(() -> new IncidenteNoEncontradoException(
                "No existe el actor " + actual.usuario()));
        incidente.marcarReintentando(actor, motivo);
        incidenteRepositorio.flush();
        servicioEvento.registrar(
            incidente.getSolicitud(),
            incidente.getRevision(),
            TipoEventoSolicitud.INCIDENTE_REINTENTANDO,
            actor,
            null,
            incidente.getRevision().getEstado(),
            incidente.getRevision().getEstado(),
            correlacion,
            "Reintento operativo solicitado: " + motivo
        );
        return detalle(incidente);
    }

    @Transactional
    public IncidenteDetalle resolver(
        UUID id,
        UsuarioActual actual,
        String resultado,
        String correlacion
    ) {
        Incidente incidente = buscarBloqueado(id);
        if (incidente.getEstado() == EstadoIncidente.RESUELTO
            || incidente.getEstado() == EstadoIncidente.CERRADO) {
            return detalle(incidente);
        }
        Usuario actor = actual == null
            ? null
            : usuarioRepositorio.findById(actual.id()).orElse(null);
        incidente.resolver(actor, resultado);
        incidenteRepositorio.flush();
        servicioEvento.registrar(
            incidente.getSolicitud(),
            incidente.getRevision(),
            TipoEventoSolicitud.INCIDENTE_RESUELTO,
            actor,
            null,
            incidente.getRevision().getEstado(),
            incidente.getRevision().getEstado(),
            correlacion,
            "Incidente resuelto: " + resultado
        );
        return detalle(incidente);
    }

    @Transactional
    public IncidenteDetalle resolverPorRevision(
        UUID revisionId,
        UsuarioActual actual,
        String resultado,
        String correlacion
    ) {
        Optional<Incidente> encontrado = incidenteRepositorio
            .findByRevisionIdAndTipo(revisionId, TipoIncidente.EJECUCION);
        if (encontrado.isEmpty()) {
            return null;
        }
        return resolver(
            encontrado.get().getId(), actual, resultado, correlacion);
    }

    @Transactional
    public IncidenteDetalle resolverCompensacionPorRevision(
        UUID revisionId,
        UsuarioActual actual,
        String resultado,
        String correlacion
    ) {
        Optional<Incidente> encontrado = incidenteRepositorio
            .findByRevisionIdAndTipo(
                revisionId, TipoIncidente.COMPENSACION);
        if (encontrado.isEmpty()) {
            return null;
        }
        return resolver(
            encontrado.get().getId(), actual, resultado, correlacion);
    }

    @Transactional
    public IncidenteDetalle registrarResultado(
        UUID id,
        String resultado,
        boolean reabrir,
        String correlacion
    ) {
        Incidente incidente = buscarBloqueado(id);
        if (reabrir) {
            incidente.reabrir(resultado);
        } else {
            incidente.registrarResultado(resultado);
        }
        incidenteRepositorio.flush();
        servicioEvento.registrar(
            incidente.getSolicitud(),
            incidente.getRevision(),
            TipoEventoSolicitud.INCIDENTE_REINTENTO_RECHAZADO,
            incidente.getActor(),
            null,
            incidente.getRevision().getEstado(),
            incidente.getRevision().getEstado(),
            correlacion,
            "Resultado de reintento: " + resultado
        );
        return detalle(incidente);
    }

    @Transactional
    public IncidenteDetalle marcarNoReintentable(
        UUID id,
        String motivo,
        String correlacion
    ) {
        Incidente incidente = buscarBloqueado(id);
        incidente.marcarNoReintentable(motivo);
        incidenteRepositorio.flush();
        servicioEvento.registrar(
            incidente.getSolicitud(),
            incidente.getRevision(),
            TipoEventoSolicitud.INCIDENTE_REINTENTO_RECHAZADO,
            null,
            null,
            incidente.getRevision().getEstado(),
            incidente.getRevision().getEstado(),
            correlacion,
            "Incidente marcado como no reintentable: " + motivo
        );
        return detalle(incidente);
    }

    @Transactional
    public void cerrarIncidenteDeIntento(
        UUID referencia,
        String motivo,
        String correlacion
    ) {
        incidenteRepositorio
            .findByTipoAndReferencia(TipoIncidente.EJECUCION, referencia)
            .ifPresent(incidente -> {
                incidente.cerrar(null, motivo);
                incidenteRepositorio.flush();
                servicioEvento.registrar(
                    incidente.getSolicitud(),
                    incidente.getRevision(),
                    TipoEventoSolicitud.INCIDENTE_RESUELTO,
                    null,
                    null,
                    incidente.getRevision().getEstado(),
                    incidente.getRevision().getEstado(),
                    correlacion,
                    "Incidente cerrado: " + motivo
                );
            });
    }

    private Incidente buscarParaReintento(UUID id) {
        Incidente incidente = incidenteRepositorio.findById(id)
            .orElseThrow(() -> new IncidenteNoEncontradoException(
                "No existe el incidente " + id));
        if (incidente.getTipo() != TipoIncidente.EJECUCION
            && incidente.getTipo() != TipoIncidente.COMPENSACION) {
            throw new ReintentoNoPermitidoException(
                "El incidente no admite reintento operativo");
        }
        if (incidente.getEstado() != EstadoIncidente.ABIERTO) {
            throw new ReintentoNoPermitidoException(
                "El incidente no está abierto: " + incidente.getEstado());
        }
        if (!incidente.isReintentable()) {
            throw new ReintentoNoPermitidoException(
                "El incidente no admite un reintento seguro");
        }
        return incidente;
    }

    private Incidente buscarParaReintentoBloqueado(UUID id) {
        Incidente incidente = buscarBloqueado(id);
        if (incidente.getTipo() != TipoIncidente.EJECUCION
            && incidente.getTipo() != TipoIncidente.COMPENSACION) {
            throw new ReintentoNoPermitidoException(
                "El incidente no admite reintento operativo");
        }
        if (incidente.getEstado() != EstadoIncidente.ABIERTO) {
            throw new ReintentoNoPermitidoException(
                "El incidente no está abierto: " + incidente.getEstado());
        }
        if (!incidente.isReintentable()) {
            throw new ReintentoNoPermitidoException(
                "El incidente no admite un reintento seguro");
        }
        return incidente;
    }

    private Incidente buscarBloqueado(UUID id) {
        return incidenteRepositorio.buscarPorIdParaActualizar(id)
            .orElseThrow(() -> new IncidenteNoEncontradoException(
                "No existe el incidente " + id));
    }

    private void exigirOperador(UsuarioActual actual) {
        if (actual == null
            || (!actual.tieneRol("OPERACIONES")
                && !actual.tieneRol("ADMINISTRADOR"))) {
            throw new AccesoDenegadoException(
                "No tiene permiso para operar incidentes");
        }
    }

    private IncidenteDetalle detalle(Incidente incidente) {
        return new IncidenteDetalle(
            incidente.getId(),
            incidente.getSolicitud().getId(),
            incidente.getRevision().getId(),
            incidente.getTipo(),
            incidente.getReferencia(),
            incidente.getEstado(),
            incidente.getMotivo(),
            incidente.getActor() == null
                ? null
                : incidente.getActor().getUsuario(),
            incidente.isReintentable(),
            incidente.getReintentos(),
            incidente.getUltimoResultado(),
            incidente.getCreadoEn(),
            incidente.getActualizadoEn()
        );
    }
}
