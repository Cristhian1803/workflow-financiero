package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import com.workflow.financiero.aplicacion.servicio.ServicioTransaccionMotorPrueba;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Verifica que el contexto arranca contra PostgreSQL y que Flyway y Flowable quedan operativos.
 */
@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AplicacionWorkflowFinancieroTests extends PruebaIntegracionBase {

    @Autowired
    ApplicationContext contexto;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("El contexto arranca con PostgreSQL y las tablas estan creadas")
    void contextoCargaConPostgres() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(contexto.getBean(RuntimeService.class)).isNotNull();
        assertThat(contexto.getBean(ServicioTransaccionMotorPrueba.class)).isNotNull();
        Integer tablas = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'solicitud_prueba'",
            Integer.class);
        assertThat(tablas).isEqualTo(1);
    }
}
