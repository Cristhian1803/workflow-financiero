package com.workflow.financiero.aplicacion.configuracion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita la configuración del proveedor financiero en todos los perfiles.
 * El simulador y sus rutas se activan por perfil en la capa web.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesProveedorFinanciero.class)
public class ConfiguracionProveedorFinanciero {
}
