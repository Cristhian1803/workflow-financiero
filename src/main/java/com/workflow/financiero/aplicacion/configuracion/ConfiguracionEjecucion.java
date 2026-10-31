package com.workflow.financiero.aplicacion.configuracion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita la configuración del trabajador de ejecución en todos los perfiles.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesEjecucion.class)
public class ConfiguracionEjecucion {
}
