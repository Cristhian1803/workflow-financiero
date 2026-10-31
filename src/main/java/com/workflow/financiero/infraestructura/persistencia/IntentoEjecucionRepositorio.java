package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntentoEjecucionRepositorio
    extends JpaRepository<IntentoEjecucion, UUID> {

    @Query("select i from IntentoEjecucion i where i.revision.id = :revisionId")
    Optional<IntentoEjecucion> buscarPorRevisionId(
        @Param("revisionId") UUID revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntentoEjecucion i where i.id = :id")
    Optional<IntentoEjecucion> buscarPorIdParaActualizar(
        @Param("id") UUID id);

    /**
     * Reclama la intención pendiente más antigua. SKIP LOCKED evita que dos
     * trabajadores se bloqueen o reclamen la misma fila.
     */
    @Query(value = """
        SELECT * FROM intento_ejecucion
        WHERE inteejec_estado = 'PENDIENTE'
        ORDER BY inteejec_creado_en
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<IntentoEjecucion> reclamarPendiente();
}
