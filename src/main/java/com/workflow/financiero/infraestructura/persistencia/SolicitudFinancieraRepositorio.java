package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudFinancieraRepositorio extends JpaRepository<SolicitudFinanciera, UUID> {

    @EntityGraph(attributePaths = {
        "solicitante",
        "revisionActual",
        "revisionActual.tipoOperacion",
        "revisionActual.moneda"
    })
    @Query("select s from SolicitudFinanciera s where s.id = :id")
    Optional<SolicitudFinanciera> buscarDetallePorId(@Param("id") UUID id);
}
