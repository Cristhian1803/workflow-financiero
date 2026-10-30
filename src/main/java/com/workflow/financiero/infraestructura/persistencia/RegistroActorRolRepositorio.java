package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.RegistroActorRol;
import com.workflow.financiero.dominio.modelo.RegistroActorRolId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroActorRolRepositorio
    extends JpaRepository<RegistroActorRol, RegistroActorRolId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO registro_actor_rol (
            regiactorol_solifina_id,
            regiactorol_usua_id,
            regiactorol_rol_codigo,
            regiactorol_fecha
        ) VALUES (
            :solicitudId,
            :usuarioId,
            :rolCodigo,
            :fecha
        )
        ON CONFLICT (regiactorol_solifina_id, regiactorol_usua_id) DO NOTHING
        """, nativeQuery = true)
    int insertarSiNoExiste(
        @Param("solicitudId") UUID solicitudId,
        @Param("usuarioId") UUID usuarioId,
        @Param("rolCodigo") String rolCodigo,
        @Param("fecha") Instant fecha
    );

    @Query("""
        select r
        from RegistroActorRol r
        join fetch r.rol
        join fetch r.usuario
        where r.solicitud.id = :solicitudId
        """)
    List<RegistroActorRol> buscarPorSolicitud(@Param("solicitudId") UUID solicitudId);
}
