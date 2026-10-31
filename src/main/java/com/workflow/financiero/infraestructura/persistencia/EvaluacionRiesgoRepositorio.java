package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.EvaluacionRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluacionRiesgoRepositorio
    extends JpaRepository<EvaluacionRiesgo, UUID> {

    @Query("""
        select e from EvaluacionRiesgo e
        where e.revision.id = :revisionId
          and e.origen = :origen
          and e.estado in :estados
        order by e.creadoEn desc
        """)
    Optional<EvaluacionRiesgo> buscarVigentePorRevision(
        @Param("revisionId") UUID revisionId,
        @Param("origen") OrigenRiesgo origen,
        @Param("estados") Collection<EstadoEvaluacionRiesgo> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select e from EvaluacionRiesgo e
        where e.revision.id = :revisionId
          and e.origen = :origen
          and e.estado in :estados
        order by e.creadoEn desc
        """)
    Optional<EvaluacionRiesgo> buscarVigentePorRevisionParaActualizar(
        @Param("revisionId") UUID revisionId,
        @Param("origen") OrigenRiesgo origen,
        @Param("estados") Collection<EstadoEvaluacionRiesgo> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EvaluacionRiesgo e where e.id = :id")
    Optional<EvaluacionRiesgo> buscarPorIdParaActualizar(
        @Param("id") UUID id);

    /**
     * Reclama una solicitud externa pendiente. SKIP LOCKED evita que dos
     * trabajadores envíen la misma solicitud al mismo tiempo.
     */
    @Query(value = """
        SELECT * FROM evaluacion_riesgo
        WHERE evalries_origen = 'EXTERNO'
          AND evalries_estado = 'PENDIENTE'
          AND evalries_identificador_evento IS NULL
          AND evalries_referencia_solicitud IS NULL
          AND evalries_intentos < :maxIntentos
          AND (
            evalries_proximo_intento_en IS NULL
            OR evalries_proximo_intento_en <= :ahora
          )
          AND (
            evalries_reclamado_en IS NULL
            OR evalries_reclamado_en <= :limite
          )
        ORDER BY COALESCE(
            evalries_proximo_intento_en,
            evalries_creado_en
        )
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<EvaluacionRiesgo> reclamarSiguiente(
        @Param("limite") OffsetDateTime limite,
        @Param("ahora") OffsetDateTime ahora,
        @Param("maxIntentos") int maxIntentos);
}
