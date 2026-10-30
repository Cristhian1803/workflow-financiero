package com.workflow.financiero.api.configuracion;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI común. La API se autentica con un token JWT enviado
 * en la cabecera Authorization.
 */
@Configuration
public class ConfiguracionOpenApi {

    private static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    OpenAPI documentacionApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Workflow Financiero API")
                .version("v1")
                .description("Backend de operaciones financieras con BPMN. "
                    + "La creación de solicitudes deja la revisión en "
                    + "EVALUANDO_RIESGO; el proceso se inicia en una etapa "
                    + "posterior."))
            .components(new Components().addSecuritySchemes(
                ESQUEMA_BEARER,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
            .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
