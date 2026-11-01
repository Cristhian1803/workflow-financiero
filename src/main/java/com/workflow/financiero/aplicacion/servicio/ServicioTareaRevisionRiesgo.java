package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.excepcion.RevisionRiesgoYaResueltaException;
import com.workflow.financiero.dominio.excepcion.TareaRevisionRiesgoNoEncontradaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.EvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TareaRevisionRiesgo;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.dominio.riesgo.ClavesRiesgoExterno;
import com.workflow.financiero.infraestructura.persistencia.EvaluacionRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaRevisionRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta y resolución de la tarea humana que reemplaza a la evaluación
 * externa cuando esta no llega. La resolución solo determina riesgo: no crea
 * decisiones de aprobación.
 */
@Service
@RequiredArgsConstructor
public class ServicioTareaRevisionRiesgo {

    private static final String ROL_ANALISTA = "ANALISTA_RIESGOS";
    private static final String ROL_JEFE = "JEFE_RIESGOS";
    private static final int LONGITUD_MAXIMA_TEXTO = 1000;

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final TareaRevisionRiesgoRepositorio tareaRepositorio;
    private final TareaAprobacionRepositorio tareaAprobacionRepositorio;
    private final EvaluacionRiesgoRepositorio evaluacionRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final CalculadoraPlanAprobacion calculadoraPlan;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioIncidente servicioIncidente;
    private final PuertoProceso puertoProceso;

    @Transactional(readOnly = true)
    public List<TareaRevisionRiesgoBandeja> listar(UsuarioActual actual) {
        verificarRol(actual);
        return tareaRepositorio
            .listarPendientes(
                actual.id(),
                EstadoTareaRevisionRiesgo.PENDIENTE,
                EstadoRevision.REVISION_MANUAL
            )
            .stream()
            .map(this::bandeja)
            .toList();
    }

    /**
     * Registra la tarea de negocio cuando Flowable crea la userTask. Es
     * idempotente para que un reintento del motor no duplique la tarea.
     */
    @Transactional
    public void registrarDesdeMotor(UUID revisionId, String identificadorMotor) {
        if (revisionId == null || identificadorMotor == null
            || identificadorMotor.isBlank()) {
            throw new IllegalArgumentException(
                "La revisión y la tarea del motor son obligatorias");
        }
        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));
        if (revision.getEstado() != EstadoRevision.REVISION_MANUAL) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no está en revisión manual: "
                    + revision.getEstado());
        }

        TareaRevisionRiesgo tarea = tareaRepositorio
            .findByRevisionId(revisionId)
            .orElse(null);
        if (tarea != null) {
            if (tarea.getIdentificadorTareaMotor() == null) {
                tarea.asignarIdentificadorMotor(identificadorMotor);
                tareaRepositorio.saveAndFlush(tarea);
            } else if (!tarea.getIdentificadorTareaMotor()
                .equals(identificadorMotor)) {
                throw new IllegalStateException(
                    "La revisión ya tiene otra tarea manual asociada");
            }
            return;
        }

        TareaRevisionRiesgo nueva = new TareaRevisionRiesgo(revision);
        nueva.asignarIdentificadorMotor(identificadorMotor);
        tareaRepositorio.saveAndFlush(nueva);
        servicioEvento.registrar(
            revision.getSolicitud(),
            revision,
            TipoEventoSolicitud.TAREA_REVISION_RIESGO_CREADA,
            null,
            null,
            EstadoRevision.REVISION_MANUAL,
            EstadoRevision.REVISION_MANUAL,
            null,
            "Tarea manual creada para la revisión " + revision.getNumero()
        );
    }

    @Transactional(readOnly = true)
    public boolean tienePendiente(UUID revisionId) {
        return tareaRepositorio.existsByRevisionIdAndEstado(
            revisionId, EstadoTareaRevisionRiesgo.PENDIENTE);
    }

    @Transactional
    public void cancelarPendiente(
        RevisionSolicitud revision,
        Usuario actor,
        Rol rol,
        String correlacion
    ) {
        List<TareaRevisionRiesgo> pendientes = tareaRepositorio
            .buscarPorRevisionYEstadoParaActualizar(
                revision.getId(), EstadoTareaRevisionRiesgo.PENDIENTE);
        if (pendientes.isEmpty()) {
            return;
        }
        pendientes.forEach(tarea ->
            tarea.cancelar("Cancelada por cancelación de la solicitud"));
        tareaRepositorio.saveAllAndFlush(pendientes);
        for (TareaRevisionRiesgo tarea : pendientes) {
            servicioEvento.registrar(
                revision.getSolicitud(),
                revision,
                TipoEventoSolicitud.TAREA_REVISION_RIESGO_CANCELADA,
                actor,
                rol,
                EstadoRevision.REVISION_MANUAL,
                EstadoRevision.REVISION_MANUAL,
                correlacion,
                "Tarea de revisión de riesgo cancelada"
            );
        }
    }

    @Transactional
    public ResultadoResolucionRevisionRiesgo resolver(
        UUID tareaId,
        UsuarioActual actual,
        ResolverRevisionRiesgoComando comando,
        String correlacion
    ) {
        verificarRol(actual);
        validarComando(comando);
        if (tareaId == null) {
            throw new TareaRevisionRiesgoNoEncontradaException();
        }

        UUID solicitudId = tareaRepositorio.buscarSolicitudIdPorTarea(tareaId)
            .orElseThrow(TareaRevisionRiesgoNoEncontradaException::new);
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(TareaRevisionRiesgoNoEncontradaException::new);

        TareaRevisionRiesgo referencia = tareaRepositorio
            .buscarDetallePorId(tareaId)
            .orElseThrow(TareaRevisionRiesgoNoEncontradaException::new);
        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(referencia.getRevision().getId())
            .orElseThrow(TareaRevisionRiesgoNoEncontradaException::new);
        TareaRevisionRiesgo tarea = tareaRepositorio
            .buscarPorIdParaActualizar(tareaId)
            .orElseThrow(TareaRevisionRiesgoNoEncontradaException::new);

        validarResolucion(solicitud, revision, tarea, actual, comando);
        NivelRiesgo consolidado = revision.getNivelRiesgo()
            .mayor(comando.nivel());
        verificarPlan(revision, consolidado);

        Usuario actor = usuarioRepositorio.findById(actual.id())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el actor " + actual.id()));
        String rolCodigo = rolRevisor(actual);

        EvaluacionRiesgo evaluacion = new EvaluacionRiesgo(
            revision,
            OrigenRiesgo.MANUAL,
            EstadoEvaluacionRiesgo.PENDIENTE,
            ClavesRiesgoExterno.manual(solicitud.getId(), revision.getId()),
            correlacion,
            "revision-manual"
        );
        evaluacionRepositorio.saveAndFlush(evaluacion);
        evaluacion.marcarManual(
            consolidado,
            "Motivo: " + comando.motivo().trim()
                + "; Evidencia: " + comando.evidencia().trim(),
            correlacion
        );
        evaluacionRepositorio.flush();

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(
                actor.getId(),
                rolCodigo,
                correlacion,
                "Revisión manual de riesgo resuelta",
                true
            )
        );
        revision.consolidarNivelRiesgo(consolidado);
        revisionRepositorio.flush();
        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.PENDIENTE_APROBACION,
            new ContextoTransicion(
                actor.getId(),
                rolCodigo,
                correlacion,
                "Plan recalculado después de la revisión manual"
            )
        );

        tarea.resolver(
            actor,
            comando.nivel(),
            comando.motivo(),
            comando.evidencia()
        );
        tareaRepositorio.saveAndFlush(tarea);

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.REVISION_RIESGO_RESUELTA,
            actor,
            rolRepositorio.findById(rolCodigo).orElse(null),
            EstadoRevision.REVISION_MANUAL,
            EstadoRevision.PENDIENTE_APROBACION,
            correlacion,
            "Riesgo manual " + comando.nivel()
                + "; riesgo consolidado " + consolidado
        );
        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.PLAN_APROBACION_RECALCULADO,
            actor,
            rolRepositorio.findById(rolCodigo).orElse(null),
            null,
            null,
            correlacion,
            "Plan recalculado: " + rolesPlan(revision, consolidado)
        );
        servicioIncidente.resolverRiesgoExternoPorRevision(
            revision.getId(),
            actual,
            "Revisión manual de riesgo resuelta",
            correlacion
        );

        completarTarea(tarea, consolidado);
        EstadoRevision estadoFinal = revisionRepositorio
            .buscarDetallePorId(revision.getId())
            .orElse(revision)
            .getEstado();
        return new ResultadoResolucionRevisionRiesgo(
            tarea.getId(),
            tarea.getEstado(),
            estadoFinal,
            tarea.getNivel(),
            tarea.getResueltoEn()
        );
    }

    public void verificarRol(UsuarioActual actual) {
        if (actual == null
            || (!actual.tieneRol(ROL_ANALISTA)
                && !actual.tieneRol(ROL_JEFE))) {
            throw new AccesoDenegadoException(
                "La revisión manual requiere un rol de Riesgos autorizado");
        }
    }

    private void validarResolucion(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        TareaRevisionRiesgo tarea,
        UsuarioActual actual,
        ResolverRevisionRiesgoComando comando
    ) {
        if (!tarea.estaPendiente()) {
            throw new RevisionRiesgoYaResueltaException(
                "La tarea de revisión ya está resuelta o cancelada");
        }
        if (solicitud.getRevisionActual() == null
            || !solicitud.getRevisionActual().getId().equals(revision.getId())) {
            throw new RevisionRiesgoYaResueltaException(
                "La revisión ya no es la actual de la solicitud");
        }
        if (revision.getEstado() != EstadoRevision.REVISION_MANUAL) {
            throw new RevisionRiesgoYaResueltaException(
                "La revisión no está en revisión manual: "
                    + revision.getEstado());
        }
        if (solicitud.getSolicitante().getId().equals(actual.id())) {
            throw new AccesoDenegadoException(
                "El creador de la solicitud no puede resolver su propia revisión");
        }
        if (revision.getNivelRiesgo() == null) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no tiene el riesgo interno clasificado");
        }
        if (comando.nivel().compareTo(revision.getNivelRiesgo()) < 0) {
            throw new DecisionInvalidaException(
                "nivelRiesgo",
                "El nivel manual no puede ser inferior al riesgo interno "
                    + revision.getNivelRiesgo()
            );
        }
        if (evaluacionRepositorio.buscarVigentePorRevision(
                revision.getId(),
                OrigenRiesgo.MANUAL,
                List.of(EstadoEvaluacionRiesgo.MANUAL))
            .isPresent()) {
            throw new RevisionRiesgoYaResueltaException(
                "La revisión manual ya fue aplicada");
        }
        if (evaluacionRepositorio.buscarVigentePorRevision(
                revision.getId(),
                OrigenRiesgo.EXTERNO,
                List.of(EstadoEvaluacionRiesgo.RECIBIDA))
            .isPresent()) {
            throw new RevisionRiesgoYaResueltaException(
                "La evaluación externa ya fue aplicada");
        }
    }

    private void verificarPlan(
        RevisionSolicitud revision,
        NivelRiesgo consolidado
    ) {
        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(revision.getMonto(), consolidado));
        Set<String> rolesActuales = tareaAprobacionRepositorio
            .buscarPorRevision(revision.getId())
            .stream()
            .map(TareaAprobacion::getRol)
            .map(Rol::getCodigo)
            .collect(Collectors.toSet());
        if (!plan.roles().equals(rolesActuales)) {
            throw new TransicionEstadoInvalidaException(
                "El plan recalculado no coincide con las tareas existentes; "
                    + "la revisión permanece en revisión manual");
        }
    }

    private void completarTarea(
        TareaRevisionRiesgo tarea,
        NivelRiesgo consolidado
    ) {
        if (tarea.getIdentificadorTareaMotor() == null
            || tarea.getIdentificadorTareaMotor().isBlank()) {
            throw new TransicionEstadoInvalidaException(
                "La tarea de revisión no tiene una tarea de motor asociada");
        }
        puertoProceso.completarTarea(
            tarea.getIdentificadorTareaMotor(),
            Map.of(
                "revisionManualResuelta", true,
                "nivelRiesgoManual", consolidado.name()
            )
        );
    }

    private void validarComando(ResolverRevisionRiesgoComando comando) {
        if (comando == null || comando.nivel() == null) {
            throw new DecisionInvalidaException(
                "nivelRiesgo", "El nivel de riesgo es obligatorio");
        }
        if (comando.motivo() == null || comando.motivo().isBlank()) {
            throw new DecisionInvalidaException(
                "motivo", "El motivo de la revisión es obligatorio");
        }
        if (comando.motivo().length() > LONGITUD_MAXIMA_TEXTO) {
            throw new DecisionInvalidaException(
                "motivo", "El motivo no puede superar los "
                    + LONGITUD_MAXIMA_TEXTO + " caracteres");
        }
        if (comando.evidencia() == null || comando.evidencia().isBlank()) {
            throw new DecisionInvalidaException(
                "evidencia", "La evidencia de la revisión es obligatoria");
        }
        if (comando.evidencia().length() > LONGITUD_MAXIMA_TEXTO) {
            throw new DecisionInvalidaException(
                "evidencia", "La evidencia no puede superar los "
                    + LONGITUD_MAXIMA_TEXTO + " caracteres");
        }
    }

    private String rolRevisor(UsuarioActual actual) {
        return actual.tieneRol(ROL_JEFE) ? ROL_JEFE : ROL_ANALISTA;
    }

    private String rolesPlan(
        RevisionSolicitud revision,
        NivelRiesgo nivel
    ) {
        return String.join(
            ", ",
            calculadoraPlan.calcular(
                new ContextoAprobacion(revision.getMonto(), nivel))
                .roles()
                .stream()
                .sorted()
                .toList()
        );
    }

    private TareaRevisionRiesgoBandeja bandeja(TareaRevisionRiesgo tarea) {
        RevisionSolicitud revision = tarea.getRevision();
        SolicitudFinanciera solicitud = revision.getSolicitud();
        return new TareaRevisionRiesgoBandeja(
            tarea.getId(),
            solicitud.getId(),
            revision.getId(),
            revision.getNumero(),
            tarea.getEstado(),
            solicitud.getSolicitante().getUsuario(),
            revision.getTipoOperacion().getCodigo(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getNivelRiesgo(),
            revision.getDescripcion(),
            revision.getJustificacion(),
            tarea.getCreadoEn()
        );
    }
}
