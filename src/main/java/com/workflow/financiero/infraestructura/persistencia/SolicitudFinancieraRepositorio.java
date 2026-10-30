package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudFinanciera s where s.id = :id")
    Optional<SolicitudFinanciera> buscarPorIdParaActualizar(@Param("id") UUID id);

    @EntityGraph(attributePaths = {
        "solicitante",
        "revisionActual",
        "revisionActual.tipoOperacion",
        "revisionActual.moneda"
    })
    @Query("""
        select s from SolicitudFinanciera s
        where s.revisionActual is not null
          and (:solicitanteId is null or s.solicitante.id = :solicitanteId)
          and (:estado is null or s.revisionActual.estado = :estado)
        """)
    Page<SolicitudFinanciera> buscarVisibles(
        @Param("solicitanteId") UUID solicitanteId,
        @Param("estado") EstadoRevision estado,
        Pageable pageable);
}
