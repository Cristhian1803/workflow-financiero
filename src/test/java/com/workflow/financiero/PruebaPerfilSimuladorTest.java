package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.workflow.financiero.api.controlador.ControladorProveedorFinancieroSimulado;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import com.workflow.financiero.aplicacion.simulador.ServicioSimuladorProveedorFinanciero;
import com.workflow.financiero.infraestructura.simulador.OperacionSimuladaRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que el simulador no se registre fuera de los perfiles permitidos.
 */
class PruebaPerfilSimuladorTest {

    @Test
    @DisplayName("El controlador no se registra con un perfil productivo")
    void controladorNoSeRegistraConPerfilProductivo() {
        new ApplicationContextRunner()
            .withUserConfiguration(ControladorProveedorFinancieroSimulado.class)
            .withBean(
                ServicioSimuladorProveedorFinanciero.class,
                () -> mock(ServicioSimuladorProveedorFinanciero.class)
            )
            .withBean(
                ConfiguracionFallosSimulador.class,
                ConfiguracionFallosSimulador::new
            )
            .run(contexto -> assertThat(contexto)
                .doesNotHaveBean(ControladorProveedorFinancieroSimulado.class));
    }

    @Test
    @DisplayName("El controlador se registra en el perfil de pruebas")
    void controladorSeRegistraEnPerfilPruebas() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=pruebas")
            .withUserConfiguration(ControladorProveedorFinancieroSimulado.class)
            .withBean(
                ServicioSimuladorProveedorFinanciero.class,
                () -> mock(ServicioSimuladorProveedorFinanciero.class)
            )
            .withBean(
                ConfiguracionFallosSimulador.class,
                ConfiguracionFallosSimulador::new
            )
            .run(contexto -> assertThat(contexto)
                .hasSingleBean(ControladorProveedorFinancieroSimulado.class));
    }

    @Test
    @DisplayName("El servicio no se registra con un perfil productivo")
    void servicioNoSeRegistraConPerfilProductivo() {
        new ApplicationContextRunner()
            .withUserConfiguration(ServicioSimuladorProveedorFinanciero.class)
            .withBean(
                OperacionSimuladaRepositorio.class,
                () -> mock(OperacionSimuladaRepositorio.class)
            )
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .run(contexto -> assertThat(contexto)
                .doesNotHaveBean(ServicioSimuladorProveedorFinanciero.class));
    }

    @Test
    @DisplayName("El servicio se registra en el perfil de pruebas")
    void servicioSeRegistraEnPerfilPruebas() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=pruebas")
            .withUserConfiguration(ServicioSimuladorProveedorFinanciero.class)
            .withBean(
                OperacionSimuladaRepositorio.class,
                () -> mock(OperacionSimuladaRepositorio.class)
            )
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .run(contexto -> assertThat(contexto)
                .hasSingleBean(ServicioSimuladorProveedorFinanciero.class));
    }
}
