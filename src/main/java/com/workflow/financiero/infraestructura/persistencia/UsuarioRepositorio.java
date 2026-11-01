package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepositorio extends JpaRepository<Usuario, java.util.UUID> {

    @EntityGraph(attributePaths = {"roles", "perfilRiesgo"})
    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    @Query("""
        select distinct u
        from Usuario u
        join u.roles r
        where r.codigo = :rolCodigo
          and r.activo = true
          and u.activo = true
        order by u.usuario asc
        """)
    List<Usuario> buscarActivosPorRol(@Param("rolCodigo") String rolCodigo);
}
