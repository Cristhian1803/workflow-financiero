package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.evento.EventoSolicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de solo adición del historial. No expone operaciones de
 * actualización ni de borrado.
 */
@Repository
public class EventoSolicitudRepositorioJpa implements EventoSolicitudRepositorio {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public EventoSolicitud guardar(EventoSolicitud evento) {
        entityManager.persist(evento);
        return evento;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoSolicitud> buscarPorSolicitud(UUID solicitudId) {
        return entityManager.createQuery(
                "select e from EventoSolicitud e "
                    + "join fetch e.solicitud "
                    + "join fetch e.revision "
                    + "left join fetch e.actor "
                    + "left join fetch e.rol "
                    + "where e.solicitud.id = :solicitudId "
                    + "order by e.creadoEn asc, e.id asc",
                EventoSolicitud.class)
            .setParameter("solicitudId", solicitudId)
            .getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoSolicitud> buscarPorRevision(UUID revisionId) {
        return entityManager.createQuery(
                "select e from EventoSolicitud e "
                    + "join fetch e.solicitud "
                    + "join fetch e.revision "
                    + "left join fetch e.actor "
                    + "left join fetch e.rol "
                    + "where e.revision.id = :revisionId "
                    + "order by e.creadoEn asc, e.id asc",
                EventoSolicitud.class)
            .setParameter("revisionId", revisionId)
            .getResultList();
    }
}
