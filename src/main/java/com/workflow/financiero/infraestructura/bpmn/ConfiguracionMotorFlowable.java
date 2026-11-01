package com.workflow.financiero.infraestructura.bpmn;

import javax.sql.DataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configura Flowable para compartir el DataSource y el gestor de transacciones de Spring.
 */
@Configuration
public class ConfiguracionMotorFlowable {

    @Bean
    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> configurarTransaccionCompartida(
        PlatformTransactionManager gestorTransacciones,
        DataSource origenDatos
    ) {
        return configuracion -> {
            configuracion.setTransactionManager(gestorTransacciones);
            configuracion.setDataSource(origenDatos);
        };
    }
}
