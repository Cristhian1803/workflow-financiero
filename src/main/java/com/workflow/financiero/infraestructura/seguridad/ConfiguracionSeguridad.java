package com.workflow.financiero.infraestructura.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad minima del esqueleto
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(autorizacion -> autorizacion
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
