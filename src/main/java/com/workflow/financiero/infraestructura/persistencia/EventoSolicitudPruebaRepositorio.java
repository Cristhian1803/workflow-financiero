package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.EventoSolicitudPrueba;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoSolicitudPruebaRepositorio extends JpaRepository<EventoSolicitudPrueba, UUID> {

    List<EventoSolicitudPrueba> findBySolicitudIdOrderByCreadoEnAsc(UUID solicitudId);

    long countBySolicitudId(UUID solicitudId);
}
