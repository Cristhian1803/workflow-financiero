package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que los datos persistidos sigan siendo coherentes antes de que el
 * proceso avance. No recalcula políticas fuera de la calculadora existente.
 */
@Service
@RequiredArgsConstructor
public class ServicioVerificacionProceso {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final CalculadoraPlanAprobacion calculadoraPlan;

    @Transactional(readOnly = true)
    public void verificarSolicitud(UUID solicitudId, UUID revisionId) {
        RevisionSolicitud revision = revisionActual(solicitudId, revisionId);

        if (revision.getMonto() == null
            || revision.getTipoOperacion() == null
            || revision.getMoneda() == null
            || revision.getEstado() == null
            || revision.getDescripcion() == null
            || revision.getDescripcion().isBlank()
            || revision.getJustificacion() == null
            || revision.getJustificacion().isBlank()) {
            throw new IllegalStateException(
                "La solicitud no tiene todos sus datos obligatorios persistidos");
        }
    }

    @Transactional(readOnly = true)
    public void verificarRiesgo(UUID solicitudId, UUID revisionId) {
        RevisionSolicitud revision = revisionActual(solicitudId, revisionId);
        if (revision.getNivelRiesgo() == null) {
            throw new IllegalStateException(
                "La revisión no tiene el nivel de riesgo clasificado");
        }
    }

    @Transactional(readOnly = true)
    public void verificarPlan(
        UUID solicitudId,
        UUID revisionId,
        Collection<String> rolesRecibidos
    ) {
        RevisionSolicitud revision = revisionActual(solicitudId, revisionId);
        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(revision.getMonto(), revision.getNivelRiesgo()));

        Set<String> recibidos = rolesRecibidos == null
            ? Set.of()
            : new LinkedHashSet<>(rolesRecibidos);
        if (!plan.roles().equals(recibidos)) {
            throw new IllegalStateException(
                "El plan de aprobación recibido no coincide con el calculado");
        }
    }

    private RevisionSolicitud revisionActual(UUID solicitudId, UUID revisionId) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null || !revision.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        return revision;
    }
}
