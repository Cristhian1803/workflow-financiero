package com.workflow.financiero;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para pruebas de integración que comparten un único PostgreSQL real.
 * No usa H2: las restricciones, FKs y tipos son los de PostgreSQL 16.
 * También genera un secreto JWT distinto por ejecución.
 */
public abstract class PruebaIntegracionBase {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final String SECRETO_JWT = generarSecreto();
    static final String SECRETO_RIESGO = generarSecreto();
    static final String EMISOR_JWT = "workflow-financiero-pruebas";
    static final long EXPIRACION_JWT_MINUTOS = 60;

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propiedadesBaseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("seguridad.jwt.secreto", () -> SECRETO_JWT);
        registro.add("seguridad.jwt.emisor", () -> EMISOR_JWT);
        registro.add(
            "seguridad.jwt.expiracion-minutos",
            () -> EXPIRACION_JWT_MINUTOS
        );
        registro.add(
            "flujo.riesgo-externo.secreto",
            () -> SECRETO_RIESGO
        );
        registro.add(
            "flujo.riesgo-externo.ventana-tiempo",
            () -> "PT5M"
        );
        registro.add(
            "flujo.riesgo-externo.reintento",
            () -> "PT0S"
        );
        registro.add(
            "flujo.riesgo-externo.maximo-intentos",
            () -> 3
        );
    }

    private static String generarSecreto() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
