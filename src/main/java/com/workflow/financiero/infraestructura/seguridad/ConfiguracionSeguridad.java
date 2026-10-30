package com.workflow.financiero.infraestructura.seguridad;

import com.workflow.financiero.api.error.ManejadorErroresSeguridad;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad sin estado basada en JWT. La autenticación HTTP Basic ya no forma
 * parte de la API.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ConfiguracionSeguridad {

    private final ManejadorErroresSeguridad manejadorErroresSeguridad;
    private final JwtAuthenticationConverter conversorAutenticacionJwt;
    private final Environment entorno;

    @Bean
    PasswordEncoder codificadorClaves() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager administradorAutenticacion(
        UsuarioDetallesService usuarioDetallesService,
        PasswordEncoder codificadorClaves
    ) {
        DaoAuthenticationProvider proveedor =
            new DaoAuthenticationProvider(usuarioDetallesService);
        proveedor.setPasswordEncoder(codificadorClaves);
        return new ProviderManager(proveedor);
    }

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        boolean documentacionPublica = entorno.acceptsProfiles(
            Profiles.of("desarrollo", "pruebas"));

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sesion -> sesion
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(autorizacion -> {
                autorizacion.requestMatchers("/actuator/health").permitAll();
                autorizacion.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/autenticacion/iniciar-sesion"
                ).permitAll();

                if (documentacionPublica) {
                    autorizacion.requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll();
                }

                autorizacion.requestMatchers("/actuator/info")
                    .hasRole("ADMINISTRADOR");
                autorizacion.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/solicitudes"
                ).hasRole("SOLICITANTE");
                autorizacion.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(conversorAutenticacionJwt))
                .authenticationEntryPoint(manejadorErroresSeguridad)
                .accessDeniedHandler(manejadorErroresSeguridad));

        return http.build();
    }
}
