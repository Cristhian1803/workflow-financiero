package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepositorio extends JpaRepository<Rol, String> {
}
