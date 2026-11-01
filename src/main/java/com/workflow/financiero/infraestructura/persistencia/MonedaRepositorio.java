package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonedaRepositorio extends JpaRepository<Moneda, String> {
}
