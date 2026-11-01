package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.TipoOperacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoOperacionRepositorio extends JpaRepository<TipoOperacion, String> {
}
