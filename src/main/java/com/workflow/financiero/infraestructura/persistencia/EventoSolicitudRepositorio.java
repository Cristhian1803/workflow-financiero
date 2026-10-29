package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.evento.EventoSolicitud;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de solo adición para el historial de solicitudes.
 */
public interface EventoSolicitudRepositorio {

    EventoSolicitud guardar(EventoSolicitud evento);

    List<EventoSolicitud> buscarPorSolicitud(UUID solicitudId);

    List<EventoSolicitud> buscarPorRevision(UUID revisionId);
}
