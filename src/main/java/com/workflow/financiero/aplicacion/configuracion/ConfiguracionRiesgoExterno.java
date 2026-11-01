package com.workflow.financiero.aplicacion.configuracion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita la configuración de la integración de riesgo externo.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesRiesgoExterno.class)
public class ConfiguracionRiesgoExterno {
}
