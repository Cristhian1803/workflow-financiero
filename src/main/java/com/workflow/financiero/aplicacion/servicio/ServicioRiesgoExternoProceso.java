package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida el resultado controlado del riesgo externo antes de permitir que la
 * revisión pase a APROBADA. La integración real del proveedor es posterior.
 */
@Service
@RequiredArgsConstructor
public class ServicioRiesgoExternoProceso {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final ServicioEventoSolicitud servicioEvento;

    @Transactional
    public void verificar(
        UUID solicitudId,
        UUID revisionId,
        Boolean resuelto,
        String nivelRiesgoExterno,
        String correlacion
    ) {
        if (!Boolean.TRUE.equals(resuelto)) {
            throw new IllegalStateException(
                "El riesgo externo todavía no está resuelto");
        }

        NivelRiesgo nivel = nivelValido(nivelRiesgoExterno);
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null || !revision.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (revision.getEstado() != EstadoRevision.PENDIENTE_APROBACION) {
            throw new TransicionEstadoInvalidaException(
                "No se puede resolver el riesgo externo en estado "
                    + revision.getEstado());
        }
        if (revision.getNivelRiesgo() == null) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no tiene el riesgo interno clasificado");
        }
        if (nivel.compareTo(revision.getNivelRiesgo()) < 0) {
            throw new IllegalStateException(
                "El riesgo externo " + nivel
                    + " no puede ser menor que el riesgo interno "
                    + revision.getNivelRiesgo());
        }

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EVALUACION_EXTERNA_RESUELTA,
            null,
            null,
            null,
            null,
            correlacion,
            "Riesgo externo recibido: " + nivel
        );
    }

    private NivelRiesgo nivelValido(String nivel) {
        if (nivel == null || nivel.isBlank()) {
            throw new IllegalStateException(
                "El nivel de riesgo externo es obligatorio");
        }
        try {
            return NivelRiesgo.valueOf(nivel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalStateException(
                "El nivel de riesgo externo no es válido: " + nivel,
                excepcion
            );
        }
    }
}
