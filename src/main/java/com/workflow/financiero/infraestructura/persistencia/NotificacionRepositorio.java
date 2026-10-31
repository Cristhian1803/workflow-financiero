package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.Notificacion;
import com.workflow.financiero.dominio.modelo.TipoNotificacion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepositorio
    extends JpaRepository<Notificacion, UUID> {

    @Query("""
        select n
        from Notificacion n
        left join fetch n.tarea t
        left join fetch t.revision r
        left join fetch r.solicitud s
        left join fetch n.destinatarioUsuario
        left join fetch n.destinatarioRol
        where n.destinatarioUsuario.id = :usuarioId
        order by n.creadoEn desc, n.id desc
        """)
    List<Notificacion> buscarPorDestinatarioUsuario(
        @Param("usuarioId") UUID usuarioId);

    @Query("""
        select n
        from Notificacion n
        left join fetch n.tarea t
        left join fetch t.revision r
        left join fetch r.solicitud s
        left join fetch n.destinatarioUsuario
        left join fetch n.destinatarioRol
        where n.destinatarioRol.codigo in :roles
        order by n.creadoEn desc, n.id desc
        """)
    List<Notificacion> buscarPorDestinatarioRol(
        @Param("roles") Collection<String> roles);

    @Query("""
        select count(n) > 0
        from Notificacion n
        where n.tarea.id = :tareaId
          and n.tipo = :tipo
          and n.destinatarioUsuario.id = :usuarioId
        """)
    boolean existePorTareaTipoYUsuario(
        @Param("tareaId") UUID tareaId,
        @Param("tipo") TipoNotificacion tipo,
        @Param("usuarioId") UUID usuarioId);

    @Query("""
        select count(n) > 0
        from Notificacion n
        where n.tarea.id = :tareaId
          and n.tipo = :tipo
          and n.destinatarioRol.codigo = :rolCodigo
        """)
    boolean existePorTareaTipoYRol(
        @Param("tareaId") UUID tareaId,
        @Param("tipo") TipoNotificacion tipo,
        @Param("rolCodigo") String rolCodigo);
}
