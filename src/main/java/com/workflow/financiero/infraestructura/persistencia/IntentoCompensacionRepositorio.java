package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.IntentoCompensacion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntentoCompensacionRepositorio
    extends JpaRepository<IntentoCompensacion, UUID> {

    @Query("""
        select c from IntentoCompensacion c
        where c.revision.id = :revisionId
        """)
    Optional<IntentoCompensacion> buscarPorRevisionId(
        @Param("revisionId") UUID revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from IntentoCompensacion c
        where c.revision.id = :revisionId
        """)
    Optional<IntentoCompensacion> buscarPorRevisionIdParaActualizar(
        @Param("revisionId") UUID revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IntentoCompensacion c where c.id = :id")
    Optional<IntentoCompensacion> buscarPorIdParaActualizar(
        @Param("id") UUID id);

    /**
     * Reclama una compensación pendiente o una liberación que quedó en curso
     * después de una caída. SKIP LOCKED evita que dos trabajadores se pisen.
     */
    @Query(value = """
        SELECT * FROM intento_compensacion
        WHERE intecomp_intentos < :maxIntentos
          AND (
            (
              intecomp_estado = 'PENDIENTE'
              AND (
                intecomp_proximo_intento_en IS NULL
                OR intecomp_proximo_intento_en <= :ahora
              )
            )
            OR
            (
              intecomp_estado = 'LIBERANDO'
              AND intecomp_reclamado_en <= :limite
            )
          )
        ORDER BY COALESCE(
            intecomp_proximo_intento_en,
            intecomp_reclamado_en,
            intecomp_creado_en
        )
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<IntentoCompensacion> reclamarSiguiente(
        @Param("limite") OffsetDateTime limite,
        @Param("ahora") OffsetDateTime ahora,
        @Param("maxIntentos") int maxIntentos);
}
