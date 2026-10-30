package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.modelo.DecisionAprobacion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.dominio.modelo.TipoDecision;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacionPorMonto;
import com.workflow.financiero.dominio.regla.aprobacion.ResultadoAutorizacion;
import com.workflow.financiero.infraestructura.persistencia.DecisionAprobacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RegistroActorRolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * Comprueba que una revisión pueda ejecutarse: que sea la actual, que no esté
 * en un estado terminal, que el plan esté completo y que cada aprobación sea
 * válida. No abre tareas ni ejecuta la operación.
 */
@Service
@RequiredArgsConstructor
public class VerificadorAutorizacionEjecucion {

    private static final String ROL_GERENTE = "GERENTE";
    private static final String ROL_RIESGOS = "RIESGOS";

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final CalculadoraPlanAprobacion calculadoraPlan;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final DecisionAprobacionRepositorio decisionRepositorio;
    private final RegistroActorRolRepositorio registroRepositorio;

    @Transactional(readOnly = true)
    public ResultadoAutorizacion verificar(UUID solicitudId, UUID revisionId) {
        if (solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias para verificar la autorización");
        }

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null || !revision.getId().equals(revisionId)) {
            return new ResultadoAutorizacion(false, List.of(
                "La revisión indicada no es la actual de la solicitud"));
        }
        if (revision.getEstado().esTerminal()) {
            return new ResultadoAutorizacion(false, List.of(
                "La revisión está en estado " + revision.getEstado()
                    + " y no puede ejecutarse"));
        }
        if (revision.getNivelRiesgo() == null) {
            return new ResultadoAutorizacion(false, List.of(
                "La revisión no tiene el riesgo clasificado"));
        }

        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(revision.getMonto(), revision.getNivelRiesgo()));

        List<TareaAprobacion> tareas = tareaRepositorio.buscarPorRevision(revisionId);
        Map<String, TareaAprobacion> tareasPorRol = tareas.stream()
            .collect(Collectors.toMap(
                tarea -> tarea.getRol().getCodigo(),
                tarea -> tarea,
                (existente, nuevo) -> existente));

        Map<UUID, DecisionAprobacion> decisionesPorTarea = decisionRepositorio
            .buscarPorRevision(revisionId)
            .stream()
            .collect(Collectors.toMap(
                decision -> decision.getTarea().getId(),
                decision -> decision,
                (existente, nuevo) -> existente));

        Map<UUID, String> rolPorActor = registroRepositorio
            .buscarPorSolicitud(solicitudId)
            .stream()
            .collect(Collectors.toMap(
                registro -> registro.getUsuario().getId(),
                registro -> registro.getRol().getCodigo(),
                (existente, nuevo) -> existente));

        Set<String> motivos = new LinkedHashSet<>();
        for (TareaAprobacion tarea : tareas) {
            if (tarea.getEstado() == EstadoTareaAprobacion.RECHAZADA) {
                motivos.add("La tarea del rol " + tarea.getRol().getCodigo()
                    + " fue rechazada");
            }
            if (tarea.getEstado() == EstadoTareaAprobacion.PENDIENTE) {
                motivos.add("La tarea del rol " + tarea.getRol().getCodigo()
                    + " sigue pendiente");
            }
        }

        Map<String, Usuario> actoresDeDecision = new HashMap<>();
        for (String rol : plan.roles()) {
            TareaAprobacion tarea = tareasPorRol.get(rol);
            if (tarea == null) {
                motivos.add("Falta la tarea de aprobación del rol " + rol);
                continue;
            }
            if (tarea.getEstado() != EstadoTareaAprobacion.APROBADA) {
                motivos.add("La tarea del rol " + rol + " no está aprobada (estado "
                    + tarea.getEstado() + ")");
                continue;
            }

            DecisionAprobacion decision = decisionesPorTarea.get(tarea.getId());
            if (decision == null) {
                motivos.add("Falta la decisión de aprobación del rol " + rol);
                continue;
            }
            if (decision.getDecision() != TipoDecision.APROBADA) {
                motivos.add("La decisión del rol " + rol + " no es aprobatoria");
                continue;
            }

            Usuario actor = decision.getActor();
            if (actor == null) {
                motivos.add("La decisión del rol " + rol + " no tiene actor");
                continue;
            }
            actoresDeDecision.put(rol, actor);

            if (actor.getId().equals(solicitud.getSolicitante().getId())) {
                motivos.add("El solicitante no puede aprobar el rol " + rol);
                continue;
            }

            String rolRegistrado = rolPorActor.get(actor.getId());
            if (rolRegistrado == null) {
                motivos.add("El actor de la decisión del rol " + rol
                    + " no tiene un rol funcional registrado en la solicitud");
                continue;
            }
            if (!rolRegistrado.equals(rol)) {
                motivos.add("El actor de la decisión del rol " + rol
                    + " ya aprobó el rol " + rolRegistrado + " en esta solicitud");
            }
        }

        if (revision.getMonto().compareTo(
            PoliticaAprobacionPorMonto.LIMITE_MULTIPLES_APROBACIONES) > 0) {
            Usuario gerente = actoresDeDecision.get(ROL_GERENTE);
            Usuario riesgos = actoresDeDecision.get(ROL_RIESGOS);
            if (gerente != null && riesgos != null
                && gerente.getId().equals(riesgos.getId())) {
                motivos.add(
                    "Gerencia y Riesgos deben ser aprobados por actores distintos");
            }
        }

        return new ResultadoAutorizacion(motivos.isEmpty(), List.copyOf(motivos));
    }
}
