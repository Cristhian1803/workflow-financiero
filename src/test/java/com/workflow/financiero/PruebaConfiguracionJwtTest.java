package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;

import com.workflow.financiero.infraestructura.seguridad.ConfiguracionJwt;
import com.workflow.financiero.infraestructura.seguridad.PropiedadesJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class PruebaConfiguracionJwtTest {

    private static final String SECRETO_VALIDO =
        "secreto-de-pruebas-con-mas-de-32-bytes-0123456789";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
        .withUserConfiguration(ConfiguracionJwt.class);

    @Test
    @DisplayName("La aplicación falla si el secreto JWT está vacío")
    void fallaConSecretoVacio() {
        contexto.withPropertyValues(
            "seguridad.jwt.secreto=",
            "seguridad.jwt.emisor=workflow-financiero",
            "seguridad.jwt.expiracion-minutos=60"
        ).run(resultado -> {
            assertThat(resultado).hasFailed();
            assertThat(resultado.getStartupFailure())
                .hasRootCauseMessage(
                    "JWT_SECRETO debe estar definido y tener al menos 32 bytes");
        });
    }

    @Test
    @DisplayName("La aplicación falla si el secreto JWT es demasiado corto")
    void fallaConSecretoCorto() {
        contexto.withPropertyValues(
            "seguridad.jwt.secreto=corto",
            "seguridad.jwt.emisor=workflow-financiero",
            "seguridad.jwt.expiracion-minutos=60"
        ).run(resultado -> {
            assertThat(resultado).hasFailed();
            assertThat(resultado.getStartupFailure())
                .hasRootCauseMessage(
                    "JWT_SECRETO debe estar definido y tener al menos 32 bytes");
        });
    }

    @Test
    @DisplayName("La aplicación falla si la expiración no es positiva")
    void fallaConExpiracionInvalida() {
        contexto.withPropertyValues(
            "seguridad.jwt.secreto=" + SECRETO_VALIDO,
            "seguridad.jwt.emisor=workflow-financiero",
            "seguridad.jwt.expiracion-minutos=0"
        ).run(resultado -> {
            assertThat(resultado).hasFailed();
            assertThat(resultado.getStartupFailure())
                .hasRootCauseMessage(
                    "JWT_EXPIRACION_MINUTOS debe ser mayor que cero");
        });
    }

    @Test
    @DisplayName("Con un secreto válido se crean el codificador y el decodificador")
    void creaComponentesConSecretoValido() {
        contexto.withPropertyValues(
            "seguridad.jwt.secreto=" + SECRETO_VALIDO,
            "seguridad.jwt.emisor=workflow-financiero",
            "seguridad.jwt.expiracion-minutos=60"
        ).run(resultado -> {
            assertThat(resultado).hasNotFailed();
            assertThat(resultado).hasSingleBean(JwtEncoder.class);
            assertThat(resultado).hasSingleBean(JwtDecoder.class);
        });
    }

    @Test
    @DisplayName("La configuración no expone el secreto en su representación")
    void noExponeElSecretoEnToString() {
        PropiedadesJwt propiedades = new PropiedadesJwt(
            SECRETO_VALIDO, "workflow-financiero", 60);

        assertThat(propiedades.toString())
            .doesNotContain(SECRETO_VALIDO)
            .contains("secreto=***");
    }
}
