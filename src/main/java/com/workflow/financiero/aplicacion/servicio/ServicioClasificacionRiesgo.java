package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.riesgo.ClasificadorRiesgoInterno;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clasifica el riesgo interno de la revisión actual y registra el resultado y
 * la política aplicada en el historial.
 */
@Service
@RequiredArgsConstructor
public class ServicioClasificacionRiesgo {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final ServicioEventoSolicitud servicioEvento;

    private final ClasificadorRiesgoInterno clasificador = new ClasificadorRiesgoInterno();

    @Transactional
    public ResultadoRiesgo clasificarYRegistrar(
        UUID solicitudId,
        UUID revisionId,
        String correlacion
    ) {
        if (solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias para clasificar el riesgo");
        }

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));

        if (solicitud.getRevisionActual() == null
            || !solicitud.getRevisionActual().getId().equals(revision.getId())) {
            throw new TransicionEstadoInvalidaException(
                "La revisión " + revisionId + " no es la actual de la solicitud " + solicitudId);
        }

        ResultadoRiesgo resultado = clasificador.clasificar(
            revision.getTipoOperacion(),
            solicitud.getSolicitante().getPerfilRiesgo(),
            revision.getMonto()
        );

        revision.registrarNivelRiesgo(resultado.nivel());

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.RIESGO_CLASIFICADO,
            null,
            null,
            null,
            null,
            correlacion,
            resultado.detalle()
        );

        return resultado;
    }
}
