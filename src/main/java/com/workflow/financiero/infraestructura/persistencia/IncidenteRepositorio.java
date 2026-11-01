package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.estado.EstadoIncidente;
import com.workflow.financiero.dominio.estado.TipoIncidente;
import com.workflow.financiero.dominio.modelo.Incidente;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenteRepositorio
    extends JpaRepository<Incidente, UUID> {

    Optional<Incidente> findByTipoAndReferencia(
        TipoIncidente tipo,
        UUID referencia);

    Optional<Incidente> findByRevisionIdAndTipo(
        UUID revisionId,
        TipoIncidente tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incidente i where i.id = :id")
    Optional<Incidente> buscarPorIdParaActualizar(@Param("id") UUID id);

    @Query("""
        select i from Incidente i
        where i.estado in :estados
        order by i.creadoEn desc, i.id
        """)
    List<Incidente> listarPorEstados(
        @Param("estados") Collection<EstadoIncidente> estados);
}
