package com.workflow.financiero.infraestructura.persistencia;

import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudPruebaRepositorio extends JpaRepository<SolicitudPrueba, UUID> {
}
