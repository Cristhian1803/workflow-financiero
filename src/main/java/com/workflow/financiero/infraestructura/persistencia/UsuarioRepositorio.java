package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<Usuario, java.util.UUID> {

    @EntityGraph(attributePaths = {"roles", "perfilRiesgo"})
    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);
}
