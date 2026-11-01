package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.estado.EstadoTareaRevisionRiesgo;
import com.workflow.financiero.dominio.modelo.TareaRevisionRiesgo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TareaRevisionRiesgoRepositorio
    extends JpaRepository<TareaRevisionRiesgo, UUID> {

    @Query("""
        select t.revision.solicitud.id
        from TareaRevisionRiesgo t
        where t.id = :id
        """)
    Optional<UUID> buscarSolicitudIdPorTarea(@Param("id") UUID id);

    Optional<TareaRevisionRiesgo> findByRevisionId(UUID revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TareaRevisionRiesgo t where t.id = :id")
    Optional<TareaRevisionRiesgo> buscarPorIdParaActualizar(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from TareaRevisionRiesgo t
        where t.revision.id = :revisionId
          and t.estado = :estado
        """)
    List<TareaRevisionRiesgo> buscarPorRevisionYEstadoParaActualizar(
        @Param("revisionId") UUID revisionId,
        @Param("estado") EstadoTareaRevisionRiesgo estado);

    @Query("""
        select t
        from TareaRevisionRiesgo t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        left join fetch t.actor
        where t.id = :id
        """)
    Optional<TareaRevisionRiesgo> buscarDetallePorId(@Param("id") UUID id);

    @Query("""
        select t
        from TareaRevisionRiesgo t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        left join fetch t.actor
        where t.estado = :estado
          and r.estado = :estadoRevision
          and s.revisionActual.id = r.id
          and s.solicitante.id <> :usuarioId
        order by t.creadoEn asc
        """)
    List<TareaRevisionRiesgo> listarPendientes(
        @Param("usuarioId") UUID usuarioId,
        @Param("estado") EstadoTareaRevisionRiesgo estado,
        @Param("estadoRevision")
        com.workflow.financiero.dominio.estado.EstadoRevision estadoRevision);

    boolean existsByRevisionIdAndEstado(
        UUID revisionId,
        EstadoTareaRevisionRiesgo estado);
}
