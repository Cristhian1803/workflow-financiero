package com.workflow.financiero.infraestructura.simulador;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperacionSimuladaRepositorio
    extends JpaRepository<OperacionSimulada, UUID> {

    Optional<OperacionSimulada> findByClave(String clave);

    Optional<OperacionSimulada> findBySolicitudIdAndRevisionIdAndAccion(
        UUID solicitudId,
        UUID revisionId,
        AccionFinanciera accion
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionSimulada o where o.clave = :clave")
    Optional<OperacionSimulada> buscarPorClaveParaActualizar(
        @Param("clave") String clave);
}
