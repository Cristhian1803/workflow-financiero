package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma la bandeja de tareas pendientes visibles para un aprobador. Solo
 * incluye la revisión actual y excluye las solicitudes creadas por el propio
 * actor.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioBandejaTareas {

    private final TareaAprobacionRepositorio tareaRepositorio;
    private final ServicioElegibilidadAprobador elegibilidad;
    private final PropiedadesAprobacion propiedadesAprobacion;

    public List<TareaAprobacionBandeja> listar(UsuarioActual actual) {
        elegibilidad.verificarPuedeUsarBandeja(actual);

        Map<UUID, TareaAprobacion> unicas = new LinkedHashMap<>();
        tareaRepositorio.buscarPendientesAsignadas(
                actual.id(), EstadoTareaAprobacion.PENDIENTE)
            .forEach(tarea -> unicas.put(tarea.getId(), tarea));

        List<String> roles = elegibilidad.rolesVisibles(actual);
        if (!roles.isEmpty()) {
            tareaRepositorio.buscarPendientesPorRoles(
                    actual.id(), EstadoTareaAprobacion.PENDIENTE, roles)
                .forEach(tarea -> unicas.putIfAbsent(tarea.getId(), tarea));
        }

        return unicas.values().stream()
            .sorted(Comparator
                .comparing(
                    TareaAprobacion::getFechaVencimiento,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TareaAprobacion::getCreadoEn))
            .map(this::bandeja)
            .toList();
    }

    private TareaAprobacionBandeja bandeja(TareaAprobacion tarea) {
        RevisionSolicitud revision = tarea.getRevision();
        SolicitudFinanciera solicitud = revision.getSolicitud();

        return new TareaAprobacionBandeja(
            tarea.getId(),
            solicitud.getId(),
            revision.getId(),
            revision.getNumero(),
            tarea.getRol().getCodigo(),
            tarea.getEstado(),
            tarea.getAsignado() == null ? null : tarea.getAsignado().getUsuario(),
            tarea.getCreadoEn(),
            tarea.getCreadoEn().plus(propiedadesAprobacion.notificacion()),
            tarea.getFechaVencimiento(),
            solicitud.getSolicitante().getUsuario(),
            revision.getTipoOperacion().getCodigo(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getNivelRiesgo(),
            revision.getDescripcion(),
            revision.getJustificacion()
        );
    }
}
