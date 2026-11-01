package com.workflow.financiero.aplicacion.configuracion;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del servidor para las políticas temporales. La auditoría de las
 * entidades sigue usando el instante actual del sistema.
 */
@Configuration
public class ConfiguracionTiempo {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
