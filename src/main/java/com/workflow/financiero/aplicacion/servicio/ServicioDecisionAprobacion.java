package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AprobacionNoPermitidaException;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.excepcion.TareaNoEncontradaException;
import com.workflow.financiero.dominio.excepcion.TareaYaResueltaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.excepcion.TransicionTareaInvalidaException;
import com.workflow.financiero.dominio.modelo.DecisionAprobacion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoDecision;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.DecisionAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve tareas de aprobación y mantiene en la misma transacción la decisión,
 * el historial, el rol funcional y el avance del motor.
 */
@Service
@RequiredArgsConstructor
public class ServicioDecisionAprobacion {

    private static final int LONGITUD_MAXIMA_TEXTO = 1000;

    private static final Map<String, String> VARIABLE_DECISION_POR_ROL = Map.of(
        "SUPERVISOR", "decisionSupervisor",
        "GERENTE", "decisionGerente",
        "RIESGOS", "decisionRiesgos"
    );

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final DecisionAprobacionRepositorio decisionRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RegistroActorRolServicio registroActorRol;
    private final ServicioElegibilidadAprobacion elegibilidad;
    private final ServicioEventoSolicitud servicioEvento;
    private final PuertoProceso puertoProceso;

    @Transactional
    public ResultadoDecisionAprobacion aprobar(
        UUID tareaId,
        UsuarioActual actual,
        String comentario,
        String correlacion
    ) {
        String motivo = normalizarComentario(comentario);

        ContextoDecision contexto = cargarYValidar(tareaId, actual);
        registroActorRol.registrarAprobacion(
            contexto.solicitud().getId(),
            actual.id(),
            contexto.tarea().getRol().getCodigo()
        );

        // La inserción nativa del registro limpia el contexto JPA; se recarga
        // la tarea bloqueada antes de modificarla.
        contexto = cargarYValidar(tareaId, actual);

        Usuario actor = usuario(actual);
        DecisionAprobacion decision = new DecisionAprobacion(
            contexto.tarea(),
            actor,
            TipoDecision.APROBADA,
            motivo,
            contexto.elegibilidad().delegado()
        );

        contexto.tarea().marcarAprobada();
        tareaRepositorio.saveAndFlush(contexto.tarea());
        decisionRepositorio.saveAndFlush(decision);
        servicioEvento.registrar(
            contexto.solicitud(),
            contexto.tarea().getRevision(),
            TipoEventoSolicitud.APROBACION_REGISTRADA,
            actor,
            contexto.tarea().getRol(),
            null,
            null,
            correlacion,
            "Aprobación registrada para el rol "
                + contexto.tarea().getRol().getCodigo()
        );
        tareaRepositorio.flush();

        completarTarea(
            contexto.tarea(),
            Map.of(variableDecision(contexto.tarea()), "APROBADA")
        );
        return resultado(tareaId, decision);
    }

    @Transactional
    public ResultadoDecisionAprobacion rechazar(
        UUID tareaId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        String motivoNormalizado = validarMotivo(motivo);

        ContextoDecision contexto = cargarYValidar(tareaId, actual);
        Usuario actor = usuario(actual);
        DecisionAprobacion decision = new DecisionAprobacion(
            contexto.tarea(),
            actor,
            TipoDecision.RECHAZADA,
            motivoNormalizado,
            contexto.elegibilidad().delegado()
        );

        contexto.tarea().marcarRechazada();
        tareaRepositorio.saveAndFlush(contexto.tarea());
        decisionRepositorio.saveAndFlush(decision);
        servicioEvento.registrar(
            contexto.solicitud(),
            contexto.tarea().getRevision(),
            TipoEventoSolicitud.RECHAZO_REGISTRADO,
            actor,
            contexto.tarea().getRol(),
            null,
            null,
            correlacion,
            motivoNormalizado
        );
        tareaRepositorio.flush();

        Map<String, Object> variables = new HashMap<>();
        variables.put(variableDecision(contexto.tarea()), "RECHAZADA");
        variables.put("tareaAprobacionId", contexto.tarea().getId().toString());
        variables.put("actorId", actual.id().toString());
        variables.put("rolFuncional", contexto.tarea().getRol().getCodigo());
        variables.put("motivo", motivoNormalizado);
        variables.put("correlacion", correlacion == null ? "" : correlacion);

        completarTarea(contexto.tarea(), variables);
        return resultado(tareaId, decision);
    }

    private ContextoDecision cargarYValidar(UUID tareaId, UsuarioActual actual) {
        if (tareaId == null) {
            throw new TareaNoEncontradaException();
        }

        UUID solicitudId = tareaRepositorio.buscarSolicitudIdPorTarea(tareaId)
            .orElseThrow(TareaNoEncontradaException::new);
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(TareaNoEncontradaException::new);

        tareaRepositorio.buscarPorIdParaActualizar(tareaId)
            .orElseThrow(TareaNoEncontradaException::new);
        TareaAprobacion tarea = tareaRepositorio.buscarDetallePorId(tareaId)
            .orElseThrow(TareaNoEncontradaException::new);

        if (!tarea.getRevision().getSolicitud().getId().equals(solicitudId)) {
            throw new TareaNoEncontradaException();
        }

        ElegibilidadAprobacion resultado = elegibilidad.evaluar(tarea, actual);
        if (!resultado.visible()) {
            throw new TareaNoEncontradaException();
        }

        RevisionSolicitud revisionActual = solicitud.getRevisionActual();
        if (revisionActual == null
            || !revisionActual.getId().equals(tarea.getRevision().getId())) {
            throw new TransicionEstadoInvalidaException(
                "La tarea no pertenece a la revisión actual de la solicitud");
        }

        if (tarea.getEstado() != EstadoTareaAprobacion.PENDIENTE
            || decisionRepositorio.buscarPorTarea(tareaId).isPresent()) {
            throw new TareaYaResueltaException();
        }

        // Una tarea pendiente no puede resolverse si la revisión ya fue
        // cancelada, rechazada o ejecutada por otra transición.
        if (revisionActual.getEstado() != EstadoRevision.PENDIENTE_APROBACION) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no está pendiente de aprobación");
        }

        if (!resultado.elegible()) {
            throw new AprobacionNoPermitidaException(resultado.motivo());
        }

        return new ContextoDecision(solicitud, tarea, resultado);
    }

    private Usuario usuario(UsuarioActual actual) {
        return usuarioRepositorio.findById(actual.id())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el usuario " + actual.id()));
    }

    private String normalizarComentario(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        if (comentario.length() > LONGITUD_MAXIMA_TEXTO) {
            throw new DecisionInvalidaException(
                "comentario",
                "El comentario no puede superar los "
                    + LONGITUD_MAXIMA_TEXTO + " caracteres"
            );
        }
        return comentario.trim();
    }

    private String validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new DecisionInvalidaException(
                "motivo",
                "El motivo del rechazo es obligatorio"
            );
        }
        if (motivo.length() > LONGITUD_MAXIMA_TEXTO) {
            throw new DecisionInvalidaException(
                "motivo",
                "El motivo no puede superar los "
                    + LONGITUD_MAXIMA_TEXTO + " caracteres"
            );
        }
        return motivo.trim();
    }

    private String variableDecision(TareaAprobacion tarea) {
        String variable = VARIABLE_DECISION_POR_ROL.get(
            tarea.getRol().getCodigo());
        if (variable == null) {
            throw new TransicionTareaInvalidaException(
                "No hay una variable de decisión para el rol "
                    + tarea.getRol().getCodigo());
        }
        return variable;
    }

    private void completarTarea(
        TareaAprobacion tarea,
        Map<String, Object> variables
    ) {
        String identificadorMotor = tarea.getIdentificadorTareaMotor();
        if (identificadorMotor == null || identificadorMotor.isBlank()) {
            throw new TransicionTareaInvalidaException(
                "La tarea no tiene una tarea de motor asociada");
        }
        puertoProceso.completarTarea(identificadorMotor, variables);
    }

    private ResultadoDecisionAprobacion resultado(
        UUID tareaId,
        DecisionAprobacion decision
    ) {
        TareaAprobacion tarea = tareaRepositorio.buscarDetallePorId(tareaId)
            .orElseThrow(TareaNoEncontradaException::new);

        return new ResultadoDecisionAprobacion(
            tarea.getId(),
            decision.getDecision(),
            decision.getFecha(),
            tarea.getEstado(),
            tarea.getRevision().getEstado(),
            decision.isDelegado()
        );
    }

    private record ContextoDecision(
        SolicitudFinanciera solicitud,
        TareaAprobacion tarea,
        ElegibilidadAprobacion elegibilidad
    ) {
    }
}
