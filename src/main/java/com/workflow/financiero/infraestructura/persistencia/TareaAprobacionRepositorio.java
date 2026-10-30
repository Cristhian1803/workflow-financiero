package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TareaAprobacionRepositorio extends JpaRepository<TareaAprobacion, UUID> {

    @Query("""
        select t
        from TareaAprobacion t
        join fetch t.rol
        where t.revision.id = :revisionId
        """)
    List<TareaAprobacion> buscarPorRevision(@Param("revisionId") UUID revisionId);
}
