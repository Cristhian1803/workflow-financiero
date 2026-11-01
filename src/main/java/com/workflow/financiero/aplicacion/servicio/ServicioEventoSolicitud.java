package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.EventoSolicitud;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra eventos inmutables del historial de una solicitud.
 */
@Service
@RequiredArgsConstructor
public class ServicioEventoSolicitud {

    private final EventoSolicitudRepositorio eventoRepositorio;

    @Transactional
    public EventoSolicitud registrar(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        TipoEventoSolicitud tipo,
        Usuario actor,
        Rol rol,
        EstadoRevision estadoAnterior,
        EstadoRevision estadoNuevo,
        String correlacion,
        String detalle
    ) {
        if (solicitud == null || revision == null || tipo == null) {
            throw new IllegalArgumentException(
                "La solicitud, la revisión y el tipo de evento son obligatorios");
        }
        if (!revision.getSolicitud().getId().equals(solicitud.getId())) {
            throw new IllegalArgumentException("La revisión no pertenece a la solicitud");
        }

        EventoSolicitud evento = new EventoSolicitud(
            solicitud,
            revision,
            tipo,
            actor,
            rol,
            estadoAnterior,
            estadoNuevo,
            correlacion,
            detalle
        );

        return eventoRepositorio.guardar(evento);
    }
}
