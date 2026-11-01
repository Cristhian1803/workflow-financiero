package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.EventoExterno;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoExternoRepositorio
    extends JpaRepository<EventoExterno, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select e from EventoExterno e
        where e.proveedor = :proveedor
          and e.identificadorEvento = :identificadorEvento
        """)
    Optional<EventoExterno> buscarPorClaveParaActualizar(
        @Param("proveedor") String proveedor,
        @Param("identificadorEvento") String identificadorEvento);

    @Query("""
        select e from EventoExterno e
        where e.proveedor = :proveedor
          and e.identificadorEvento = :identificadorEvento
        """)
    Optional<EventoExterno> buscarPorClave(
        @Param("proveedor") String proveedor,
        @Param("identificadorEvento") String identificadorEvento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoExterno e where e.id = :id")
    Optional<EventoExterno> buscarPorIdParaActualizar(@Param("id") UUID id);

    /**
     * Inserta el evento sin romper la transacción si otro proceso ganó la
     * carrera con el mismo proveedor e identificador.
     */
    @Modifying
    @Query(value = """
        INSERT INTO evento_externo (
            evenexte_id,
            evenexte_proveedor,
            evenexte_identificador_evento,
            evenexte_identificador_solicitud,
            evenexte_numero_revision,
            evenexte_solifina_id,
            evenexte_revisoli_id,
            evenexte_huella,
            evenexte_estado,
            evenexte_nivel_riesgo,
            evenexte_resultado,
            evenexte_fecha_proveedor,
            evenexte_correlacion,
            evenexte_intentos,
            evenexte_proximo_intento_en,
            evenexte_conflictos,
            evenexte_version,
            evenexte_recibido_en
        ) VALUES (
            :id,
            :proveedor,
            :identificadorEvento,
            :identificadorSolicitud,
            :numeroRevision,
            :solicitudId,
            :revisionId,
            :huella,
            'PENDIENTE',
            :nivelRiesgo,
            :resultado,
            :fechaProveedor,
            :correlacion,
            0,
            :proximoIntentoEn,
            0,
            0,
            :recibidoEn
        )
        ON CONFLICT (evenexte_proveedor, evenexte_identificador_evento)
        DO NOTHING
        """, nativeQuery = true)
    int insertarSiNoExiste(
        @Param("id") UUID id,
        @Param("proveedor") String proveedor,
        @Param("identificadorEvento") String identificadorEvento,
        @Param("identificadorSolicitud") UUID identificadorSolicitud,
        @Param("numeroRevision") Integer numeroRevision,
        @Param("solicitudId") UUID solicitudId,
        @Param("revisionId") UUID revisionId,
        @Param("huella") String huella,
        @Param("nivelRiesgo") String nivelRiesgo,
        @Param("resultado") String resultado,
        @Param("fechaProveedor") OffsetDateTime fechaProveedor,
        @Param("correlacion") String correlacion,
        @Param("proximoIntentoEn") OffsetDateTime proximoIntentoEn,
        @Param("recibidoEn") OffsetDateTime recibidoEn);

    @Query(value = """
        SELECT * FROM evento_externo
        WHERE evenexte_estado = 'PENDIENTE'
          AND evenexte_intentos < :maxIntentos
          AND (
            evenexte_proximo_intento_en IS NULL
            OR evenexte_proximo_intento_en <= :ahora
          )
        ORDER BY evenexte_recibido_en
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<EventoExterno> reclamarSiguiente(
        @Param("ahora") OffsetDateTime ahora,
        @Param("maxIntentos") int maxIntentos);
}
