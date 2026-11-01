package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilRiesgoRepositorio extends JpaRepository<PerfilRiesgo, String> {
}
