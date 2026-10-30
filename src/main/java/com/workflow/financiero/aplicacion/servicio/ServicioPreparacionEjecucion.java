package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoEjecucionFinanciera;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la preparación y el resultado de una ejecución controlada. No crea
 * intentos de ejecución ni llama a un proveedor financiero.
 */
@Service
@RequiredArgsConstructor
public class ServicioPreparacionEjecucion {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final PuertoEjecucionFinanciera puertoEjecucion;

    @Transactional
    public void preparar(UUID solicitudId, UUID revisionId, String correlacion) {
        RevisionSolicitud revision = revisionAprobada(solicitudId, revisionId);
        SolicitudFinanciera solicitud = revision.getSolicitud();

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EJECUCION_PREPARADA,
            null,
            null,
            null,
            null,
            correlacion,
            "Ejecución controlada preparada"
        );
        puertoEjecucion.preparar(solicitudId, revisionId);
    }

    @Transactional
    public void registrarResultado(UUID solicitudId, UUID revisionId, String correlacion) {
        RevisionSolicitud revision = revisionAprobada(solicitudId, revisionId);
        SolicitudFinanciera solicitud = revision.getSolicitud();

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EJECUCION_CONTROLADA,
            null,
            null,
            null,
            null,
            correlacion,
            "Resultado controlado registrado"
        );
        puertoEjecucion.registrarResultado(solicitudId, revisionId);
    }

    private RevisionSolicitud revisionAprobada(UUID solicitudId, UUID revisionId) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null || !revision.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (revision.getEstado() != EstadoRevision.APROBADA) {
            throw new TransicionEstadoInvalidaException(
                "No se puede preparar la ejecución en estado " + revision.getEstado());
        }
        return revision;
    }
}
