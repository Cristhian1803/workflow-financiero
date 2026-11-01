package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.autenticacion.IniciarSesionPeticion;
import com.workflow.financiero.api.contrato.autenticacion.IniciarSesionRespuesta;
import com.workflow.financiero.api.contrato.autenticacion.UsuarioActualRespuesta;
import com.workflow.financiero.aplicacion.servicio.ServicioAutenticacion;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.TokenEmitido;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio de sesión y datos del usuario autenticado.
 */
@RestController
@RequestMapping("/api/v1/autenticacion")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión y usuario actual")
public class ControladorAutenticacion {

    private static final String TIPO_TOKEN = "Bearer";

    private final ServicioAutenticacion servicioAutenticacion;
    private final ServicioUsuarioActual servicioUsuarioActual;

    @PostMapping("/iniciar-sesion")
    @Operation(
        summary = "Iniciar sesión",
        description = "Emite un token JWT firmado con los roles vigentes."
    )
    @SecurityRequirements
    IniciarSesionRespuesta iniciarSesion(
        @Valid @RequestBody IniciarSesionPeticion peticion
    ) {
        TokenEmitido token = servicioAutenticacion.iniciarSesion(
            peticion.usuario(), peticion.clave());
        return new IniciarSesionRespuesta(
            token.token(), TIPO_TOKEN, token.expiraEn());
    }

    @GetMapping("/yo")
    @Operation(summary = "Consultar el usuario autenticado")
    UsuarioActualRespuesta usuarioActual(@AuthenticationPrincipal Jwt jwt) {
        UsuarioActual usuario = servicioUsuarioActual.obtenerActual(jwt.getSubject());
        return new UsuarioActualRespuesta(
            usuario.id(),
            usuario.usuario(),
            usuario.nombre(),
            usuario.roles(),
            usuario.perfilRiesgo()
        );
    }
}
