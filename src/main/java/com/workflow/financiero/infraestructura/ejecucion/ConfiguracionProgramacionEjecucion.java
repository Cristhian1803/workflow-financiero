package com.workflow.financiero.infraestructura.ejecucion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la planificación de tareas. El planificador de ejecución solo se
 * registra cuando su propiedad está activa.
 */
@Configuration
@EnableScheduling
public class ConfiguracionProgramacionEjecucion {
}
