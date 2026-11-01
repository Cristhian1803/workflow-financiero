package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
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
    @Query("select i from IntentoEjecucion i where i.revision.id = :revisionId")
    Optional<IntentoEjecucion> buscarPorRevisionIdParaActualizar(
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

    /**
     * Reclama una intención que quedó en envío después de una caída o cuyo
     * resultado desconocido ya puede volver a consultarse. El plazo de
     * reclamación evita pisar a un trabajador vivo.
     */
    @Query(value = """
        SELECT * FROM intento_ejecucion
        WHERE inteejec_intentos < :maxIntentos
          AND (
            (
              inteejec_estado = 'ENVIANDO'
              AND inteejec_reclamado_en <= :limite
            )
            OR
            (
              inteejec_estado = 'RESULTADO_DESCONOCIDO'
              AND inteejec_proximo_intento_en IS NOT NULL
              AND inteejec_proximo_intento_en <= :ahora
            )
          )
        ORDER BY COALESCE(
            inteejec_proximo_intento_en,
            inteejec_reclamado_en,
            inteejec_creado_en
        )
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<IntentoEjecucion> reclamarRecuperable(
        @Param("limite") OffsetDateTime limite,
        @Param("ahora") OffsetDateTime ahora,
        @Param("maxIntentos") int maxIntentos);
}
