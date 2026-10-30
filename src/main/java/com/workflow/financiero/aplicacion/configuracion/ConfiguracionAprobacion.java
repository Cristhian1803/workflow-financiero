package com.workflow.financiero.aplicacion.configuracion;

import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacionPorMonto;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacionPorRiesgo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Políticas que participan en el plan de aprobación. Agregar una nueva regla
 * solo requiere registrar otro bean sin modificar el coordinador.
 */
@Configuration
public class ConfiguracionAprobacion {

    @Bean
    @Order(10)
    public PoliticaAprobacion politicaAprobacionPorMonto() {
        return new PoliticaAprobacionPorMonto();
    }

    @Bean
    @Order(20)
    public PoliticaAprobacion politicaAprobacionPorRiesgo() {
        return new PoliticaAprobacionPorRiesgo();
    }
}
