package com.workflow.financiero.infraestructura.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configura la seguridad del entorno de demostracion.
 * Habilita HTTP Basic para el usuario definido en el perfil activo y deja
 * publicos unicamente los endpoints de salud.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Habilita HTTP Basic para que el usuario configurado en el perfil pueda autenticarse.
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests(autorizacion -> autorizacion
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
