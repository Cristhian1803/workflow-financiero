package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevisionSolicitudRepositorio extends JpaRepository<RevisionSolicitud, UUID> {

    @EntityGraph(attributePaths = {"tipoOperacion", "moneda", "solicitud", "solicitud.solicitante"})
    @Query("select r from RevisionSolicitud r where r.id = :id")
    Optional<RevisionSolicitud> buscarDetallePorId(@Param("id") UUID id);
}
