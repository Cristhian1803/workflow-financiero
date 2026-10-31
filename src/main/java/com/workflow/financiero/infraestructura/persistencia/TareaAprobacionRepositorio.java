package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.estado.EstadoTareaAprobacion;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TareaAprobacionRepositorio extends JpaRepository<TareaAprobacion, UUID> {

    @Query("""
        select t.revision.solicitud.id
        from TareaAprobacion t
        where t.id = :tareaId
        """)
    Optional<UUID> buscarSolicitudIdPorTarea(@Param("tareaId") UUID tareaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TareaAprobacion t where t.id = :tareaId")
    Optional<TareaAprobacion> buscarPorIdParaActualizar(@Param("tareaId") UUID tareaId);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        join fetch t.rol
        left join fetch t.asignado
        where t.id = :tareaId
        """)
    Optional<TareaAprobacion> buscarDetallePorId(@Param("tareaId") UUID tareaId);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch t.rol
        left join fetch t.asignado
        where t.identificadorTareaMotor = :identificador
        """)
    Optional<TareaAprobacion> buscarPorIdentificadorTareaMotor(
        @Param("identificador") String identificador);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        join fetch t.rol
        left join fetch t.asignado
        where t.revision.id = :revisionId
        """)
    List<TareaAprobacion> buscarPorRevision(@Param("revisionId") UUID revisionId);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        join fetch t.rol
        left join fetch t.asignado
        where t.revision.id = :revisionId
          and t.rol.codigo = :rolCodigo
        """)
    Optional<TareaAprobacion> buscarPorRevisionYRol(
        @Param("revisionId") UUID revisionId,
        @Param("rolCodigo") String rolCodigo);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        join fetch t.rol
        join fetch t.asignado a
        where t.estado = :estado
          and s.revisionActual.id = r.id
          and s.solicitante.id <> :usuarioId
          and a.id = :usuarioId
        order by t.fechaVencimiento asc, t.creadoEn asc
        """)
    List<TareaAprobacion> buscarPendientesAsignadas(
        @Param("usuarioId") UUID usuarioId,
        @Param("estado") EstadoTareaAprobacion estado);

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.revision r
        join fetch r.solicitud s
        join fetch s.solicitante
        join fetch r.tipoOperacion
        join fetch r.moneda
        join fetch t.rol
        left join fetch t.asignado
        where t.estado = :estado
          and s.revisionActual.id = r.id
          and s.solicitante.id <> :usuarioId
          and t.asignado is null
          and t.rol.codigo in :roles
        order by t.fechaVencimiento asc, t.creadoEn asc
        """)
    List<TareaAprobacion> buscarPendientesPorRoles(
        @Param("usuarioId") UUID usuarioId,
        @Param("estado") EstadoTareaAprobacion estado,
        @Param("roles") Collection<String> roles);

    @Query("""
        select count(t) > 0
        from TareaAprobacion t
        where t.revision.id = :revisionId
          and t.asignado.id = :usuarioId
        """)
    boolean existeTareaAsignada(
        @Param("revisionId") UUID revisionId,
        @Param("usuarioId") UUID usuarioId);

    @Query("""
        select count(t) > 0
        from TareaAprobacion t
        where t.revision.id = :revisionId
          and t.asignado is null
          and t.rol.codigo in :roles
        """)
    boolean existeTareaPorRoles(
        @Param("revisionId") UUID revisionId,
        @Param("roles") Collection<String> roles);
}
