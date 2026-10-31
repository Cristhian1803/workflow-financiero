package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoCancelacionProceso;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoIntentoEjecucion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.CancelacionNoPermitidaException;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.IntentoEjecucionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
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
 * Pasos transaccionales de la cancelación. La consulta y la liberación al
 * proveedor ocurren fuera de estas transacciones.
 */
@Service
@RequiredArgsConstructor
public class ServicioIntencionCancelacion {

    private static final int LONGITUD_MAXIMA_MOTIVO = 1000;
    private static final String ROL_SOLICITANTE = "SOLICITANTE";
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final IntentoEjecucionRepositorio intentoRepositorio;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioIncidente servicioIncidente;
    private final PuertoCancelacionProceso puertoCancelacion;

    @Transactional
    public PreparacionCancelacion preparar(
        UUID solicitudId,
        UUID revisionEsperadaId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        if (solicitudId == null || actual == null) {
            throw new IllegalArgumentException(
                "La solicitud y el actor son obligatorios");
        }
        String motivoNormalizado = validarMotivo(motivo);

        SolicitudFinanciera solicitud = bloquearSolicitud(solicitudId);
        boolean propietario = verificarAutorizacion(solicitud, actual);
        RevisionSolicitud revision = bloquearRevisionActual(
            solicitud, revisionEsperadaId);

        IntentoEjecucion intento = intentoRepositorio
            .buscarPorRevisionIdParaActualizar(revision.getId())
            .orElse(null);

        EstadoRevision estado = revision.getEstado();
        if (estado.esTerminal()) {
            throw new CancelacionNoPermitidaException(
                "La revisión ya está en estado " + estado);
        }
        if (estado == EstadoRevision.REVISION_MANUAL) {
            throw new CancelacionNoPermitidaException(
                "La revisión requiere el procedimiento de revisión manual");
        }
        if (estado == EstadoRevision.COMPENSANDO
            || (intento != null
                && intento.getEstado() == EstadoIntentoEjecucion.COMPENSANDO)) {
            return preparacionEnCurso(
                solicitud, revision, intento, actual, motivoNormalizado,
                correlacion);
        }

        validarIntentoParaCancelar(intento);

        if (estado == EstadoRevision.EJECUTANDO
            || estado == EstadoRevision.RESULTADO_DESCONOCIDO
            || (intento != null
                && intento.getEstado() == EstadoIntentoEjecucion.FALLIDA)) {
            return prepararCompensacion(
                solicitud, revision, intento, actual, propietario,
                motivoNormalizado, correlacion);
        }

        return prepararCancelacionDirecta(
            solicitud, revision, intento, actual, propietario,
            motivoNormalizado, correlacion);
    }

    /**
     * Registra la liberación confirmada y cierra la revisión. Si no había
     * reserva, la intención queda cancelada sin referencia de liberación.
     */
    @Transactional
    public ResultadoCancelacionSolicitud confirmarCompensacion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera liberacion
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(
            preparacion.idSolicitud());
        RevisionSolicitud revision = bloquearRevision(
            preparacion.idRevision());
        validarRevisionActual(solicitud, revision);

        if (revision.getEstado() == EstadoRevision.CANCELADA) {
            return new ResultadoCancelacionSolicitud(
                solicitud.getId(), revision.getId(), EstadoRevision.CANCELADA);
        }
        if (revision.getEstado() != EstadoRevision.COMPENSANDO) {
            throw new CancelacionNoPermitidaException(
                "La revisión no está compensando: " + revision.getEstado());
        }

        IntentoEjecucion intento = preparacion.idIntento() == null
            ? null
            : bloquearIntento(preparacion.idIntento());
        if (intento != null) {
            if (liberacion == null) {
                if (intento.getEstado() == EstadoIntentoEjecucion.COMPENSANDO) {
                    intento.marcarCanceladaSinCompensacion();
                } else if (intento.getEstado()
                    != EstadoIntentoEjecucion.CANCELADA) {
                    throw new CancelacionNoPermitidaException(
                        "La intención no permite cerrar la compensación: "
                            + intento.getEstado());
                }
            } else {
                validarLiberacion(preparacion, liberacion);
                if (intento.getEstado() == EstadoIntentoEjecucion.COMPENSANDO
                    || intento.getEstado() == EstadoIntentoEjecucion.CANCELADA) {
                    intento.marcarCompensada(liberacion.referencia());
                } else if (intento.getEstado()
                    != EstadoIntentoEjecucion.COMPENSADA) {
                    throw new CancelacionNoPermitidaException(
                        "La intención no permite registrar la liberación: "
                            + intento.getEstado());
                }
            }
            intentoRepositorio.flush();
        }

        Usuario actor = usuarioRepositorio.findById(preparacion.actual().id())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el actor " + preparacion.actual().id()));
        Rol rol = rolRepositorio.findById(
            rolDe(preparacion.actual(), solicitud)).orElse(null);
        EstadoRevision anterior = revision.getEstado();

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.CANCELADA,
            new ContextoTransicion(
                actor.getId(),
                rol == null ? null : rol.getCodigo(),
                preparacion.correlacion(),
                "Solicitud cancelada: " + preparacion.motivo()
            )
        );

        String detalle = liberacion == null
            ? "Compensación confirmada sin reserva pendiente"
            : "Reserva liberada con referencia " + liberacion.referencia();
        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.COMPENSACION_CONFIRMADA,
            actor,
            rol,
            anterior,
            EstadoRevision.CANCELADA,
            preparacion.correlacion(),
            detalle
        );

        servicioIncidente.resolverPorRevision(
            revision.getId(),
            preparacion.actual(),
            "Cancelación con compensación",
            preparacion.correlacion()
        );

        solicitarCancelacionProceso(revision, preparacion.motivo());

        return new ResultadoCancelacionSolicitud(
            solicitud.getId(), revision.getId(), EstadoRevision.CANCELADA);
    }

    /**
     * Deja constancia de que la liberación no se pudo confirmar. La revisión
     * permanece en COMPENSANDO y la recuperación se completa en I-19b.
     */
    @Transactional
    public void marcarCompensacionPendiente(
        PreparacionCancelacion preparacion,
        String error
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(
            preparacion.idSolicitud());
        RevisionSolicitud revision = bloquearRevision(
            preparacion.idRevision());
        validarRevisionActual(solicitud, revision);

        if (revision.getEstado() != EstadoRevision.COMPENSANDO) {
            return;
        }

        IntentoEjecucion intento = preparacion.idIntento() == null
            ? null
            : bloquearIntento(preparacion.idIntento());
        if (intento != null
            && intento.getEstado() == EstadoIntentoEjecucion.COMPENSANDO) {
            intento.registrarErrorCompensacion(error);
            intentoRepositorio.flush();
        }

        Usuario actor = usuarioRepositorio.findById(preparacion.actual().id())
            .orElse(null);
        Rol rol = rolRepositorio.findById(
            rolDe(preparacion.actual(), solicitud)).orElse(null);
        String detalle = error == null || error.isBlank()
            ? "No se pudo confirmar la liberación"
            : error;

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.COMPENSACION_PENDIENTE,
            actor,
            rol,
            revision.getEstado(),
            revision.getEstado(),
            preparacion.correlacion(),
            "Compensación pendiente: " + detalle
        );
    }

    private PreparacionCancelacion prepararCancelacionDirecta(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        IntentoEjecucion intento,
        UsuarioActual actual,
        boolean propietario,
        String motivo,
        String correlacion
    ) {
        Usuario actor = cargarActor(actual);
        Rol rol = cargarRol(propietario);
        cancelarTareas(solicitud, revision, actor, rol, correlacion);

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.CANCELACION_SOLICITADA,
            actor,
            rol,
            revision.getEstado(),
            EstadoRevision.CANCELADA,
            correlacion,
            "Cancelación solicitada: " + motivo
        );

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.CANCELADA,
            new ContextoTransicion(
                actor.getId(),
                rol == null ? null : rol.getCodigo(),
                correlacion,
                "Solicitud cancelada: " + motivo
            )
        );

        if (intento != null) {
            intento.marcarCancelada(actor, motivo);
            intentoRepositorio.flush();
        }

        servicioIncidente.resolverPorRevision(
            revision.getId(),
            actual,
            "Cancelación sin ejecución",
            correlacion
        );
        solicitarCancelacionProceso(revision, motivo);

        return new PreparacionCancelacion(
            solicitud.getId(),
            revision.getId(),
            intento == null ? null : intento.getId(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            motivo,
            correlacion,
            actual,
            EstadoRevision.CANCELADA,
            false,
            false
        );
    }

    private PreparacionCancelacion prepararCompensacion(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        IntentoEjecucion intento,
        UsuarioActual actual,
        boolean propietario,
        String motivo,
        String correlacion
    ) {
        Usuario actor = cargarActor(actual);
        Rol rol = cargarRol(propietario);
        cancelarTareas(solicitud, revision, actor, rol, correlacion);

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.CANCELACION_SOLICITADA,
            actor,
            rol,
            revision.getEstado(),
            EstadoRevision.COMPENSANDO,
            correlacion,
            "Cancelación solicitada: " + motivo
        );

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.COMPENSANDO,
            new ContextoTransicion(
                actor.getId(),
                rol == null ? null : rol.getCodigo(),
                correlacion,
                "Compensación iniciada: " + motivo
            )
        );

        boolean intencionEnCompensacion = false;
        if (intento != null) {
            if (intento.getEstado() == EstadoIntentoEjecucion.FALLIDA) {
                intento.marcarCompensando(actor, motivo);
                intencionEnCompensacion = true;
            } else if (intento.getEstado()
                == EstadoIntentoEjecucion.PENDIENTE) {
                intento.marcarCancelada(actor, motivo);
            } else {
                throw new CancelacionNoPermitidaException(
                    "La intención no permite iniciar la compensación: "
                        + intento.getEstado());
            }
            intentoRepositorio.flush();
        }

        return new PreparacionCancelacion(
            solicitud.getId(),
            revision.getId(),
            intento == null ? null : intento.getId(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            motivo,
            correlacion,
            actual,
            EstadoRevision.COMPENSANDO,
            true,
            intencionEnCompensacion
        );
    }

    private PreparacionCancelacion preparacionEnCurso(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        IntentoEjecucion intento,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        return new PreparacionCancelacion(
            solicitud.getId(),
            revision.getId(),
            intento == null ? null : intento.getId(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            motivo,
            correlacion,
            actual,
            EstadoRevision.COMPENSANDO,
            false,
            intento != null
                && intento.getEstado() == EstadoIntentoEjecucion.COMPENSANDO
        );
    }

    private void cancelarTareas(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        Usuario actor,
        Rol rol,
        String correlacion
    ) {
        List<TareaAprobacion> pendientes = tareaRepositorio
            .buscarPorRevisionParaActualizar(revision.getId())
            .stream()
            .filter(tarea -> tarea.getEstado() == EstadoTareaAprobacion.PENDIENTE)
            .toList();
        if (pendientes.isEmpty()) {
            return;
        }

        pendientes.forEach(TareaAprobacion::cancelar);
        tareaRepositorio.saveAllAndFlush(pendientes);

        for (TareaAprobacion tarea : pendientes) {
            servicioEvento.registrar(
                solicitud,
                revision,
                TipoEventoSolicitud.TAREA_CANCELADA,
                actor,
                tarea.getRol() == null ? rol : tarea.getRol(),
                null,
                null,
                correlacion,
                "Tarea de " + tarea.getRol().getCodigo()
                    + " cancelada por cancelación de la solicitud"
            );
        }
    }

    private void validarIntentoParaCancelar(IntentoEjecucion intento) {
        if (intento == null) {
            return;
        }
        switch (intento.getEstado()) {
            case CONFIRMADA -> throw new CancelacionNoPermitidaException(
                "La ejecución ya fue confirmada por el proveedor");
            case ENVIANDO, RESULTADO_DESCONOCIDO ->
                throw new CancelacionNoPermitidaException(
                    "El resultado de la confirmación es incierto; "
                        + "la cancelación requiere reconciliación");
            case COMPENSADA, CANCELADA ->
                throw new CancelacionNoPermitidaException(
                    "La revisión ya fue cancelada");
            case COMPENSANDO -> {
                // La revisión se trata como compensación en curso.
            }
            case PENDIENTE, FALLIDA -> {
                // Se resuelve en el camino directo o de compensación.
            }
        }
    }

    private void validarLiberacion(
        PreparacionCancelacion preparacion,
        ResultadoOperacionFinanciera liberacion
    ) {
        String claveEsperada = ClavesOperacionFinanciera.liberacion(
            preparacion.idSolicitud(), preparacion.idRevision());
        String huellaEsperada = HuellaOperacionFinanciera.calcular(
            AccionFinanciera.LIBERACION,
            preparacion.idSolicitud(),
            preparacion.idRevision(),
            preparacion.monto(),
            preparacion.moneda(),
            preparacion.tipoOperacion()
        );
        if (liberacion.accion() != AccionFinanciera.LIBERACION
            || !claveEsperada.equals(liberacion.clave())
            || !huellaEsperada.equals(liberacion.huella())
            || liberacion.estado() != EstadoOperacionFinanciera.LIBERADA) {
            throw new ConflictoProveedorFinancieroException(
                "La liberación no corresponde a la intención o no quedó confirmada");
        }
    }

    private SolicitudFinanciera bloquearSolicitud(UUID solicitudId) {
        return solicitudRepositorio.buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(SolicitudNoEncontradaException::new);
    }

    private RevisionSolicitud bloquearRevisionActual(
        SolicitudFinanciera solicitud,
        UUID revisionEsperadaId
    ) {
        RevisionSolicitud actual = solicitud.getRevisionActual();
        if (actual == null) {
            throw new CancelacionNoPermitidaException(
                "La solicitud no tiene una revisión actual");
        }
        if (revisionEsperadaId != null
            && !actual.getId().equals(revisionEsperadaId)) {
            throw new CancelacionNoPermitidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        return bloquearRevision(actual.getId());
    }

    private RevisionSolicitud bloquearRevision(UUID revisionId) {
        return revisionRepositorio.buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));
    }

    private IntentoEjecucion bloquearIntento(UUID intentoId) {
        return intentoRepositorio.buscarPorIdParaActualizar(intentoId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la intención " + intentoId));
    }

    private void validarRevisionActual(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
        RevisionSolicitud actual = solicitud.getRevisionActual();
        if (actual == null || !actual.getId().equals(revision.getId())) {
            throw new CancelacionNoPermitidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
    }

    private Usuario cargarActor(UsuarioActual actual) {
        return usuarioRepositorio.findById(actual.id())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el actor " + actual.id()));
    }

    private Rol cargarRol(boolean propietario) {
        return rolRepositorio.findById(
            propietario ? ROL_SOLICITANTE : ROL_ADMINISTRADOR
        ).orElse(null);
    }

    private String rolDe(UsuarioActual actual, SolicitudFinanciera solicitud) {
        return solicitud.getSolicitante().getId().equals(actual.id())
            ? ROL_SOLICITANTE
            : ROL_ADMINISTRADOR;
    }

    private boolean verificarAutorizacion(
        SolicitudFinanciera solicitud,
        UsuarioActual actual
    ) {
        boolean propietario = solicitud.getSolicitante().getId()
            .equals(actual.id());
        if (propietario || actual.tieneRol(ROL_ADMINISTRADOR)) {
            return propietario;
        }
        if (actual.tieneRol(ROL_SOLICITANTE)) {
            throw new SolicitudNoEncontradaException();
        }
        throw new AccesoDenegadoException(
            "Solo el dueño o un administrador pueden cancelar la solicitud");
    }

    private void solicitarCancelacionProceso(
        RevisionSolicitud revision,
        String motivo
    ) {
        if (revision.getIdentificadorProceso() != null
            && !revision.getIdentificadorProceso().isBlank()) {
            puertoCancelacion.solicitarCancelacion(
                revision.getIdentificadorProceso(), motivo);
        }
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
