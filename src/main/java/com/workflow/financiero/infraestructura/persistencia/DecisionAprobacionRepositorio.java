package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.DecisionAprobacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionAprobacionRepositorio
    extends JpaRepository<DecisionAprobacion, UUID> {

    @Query("""
        select d
        from DecisionAprobacion d
        join fetch d.actor
        join fetch d.tarea t
        where t.revision.id = :revisionId
        """)
    List<DecisionAprobacion> buscarPorRevision(@Param("revisionId") UUID revisionId);

    @Query("""
        select d
        from DecisionAprobacion d
        join fetch d.actor
        where d.tarea.id = :tareaId
        """)
    Optional<DecisionAprobacion> buscarPorTarea(@Param("tareaId") UUID tareaId);
}
