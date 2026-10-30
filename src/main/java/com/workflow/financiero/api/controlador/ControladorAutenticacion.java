package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.autenticacion.IniciarSesionPeticion;
import com.workflow.financiero.api.contrato.autenticacion.IniciarSesionRespuesta;
import com.workflow.financiero.api.contrato.autenticacion.UsuarioActualRespuesta;
import com.workflow.financiero.aplicacion.servicio.ServicioAutenticacion;
import com.workflow.financiero.aplicacion.servicio.TokenEmitido;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.Usuario;
import jakarta.validation.Valid;
import java.util.List;
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
public class ControladorAutenticacion {

    private static final String TIPO_TOKEN = "Bearer";

    private final ServicioAutenticacion servicioAutenticacion;

    @PostMapping("/iniciar-sesion")
    IniciarSesionRespuesta iniciarSesion(
        @Valid @RequestBody IniciarSesionPeticion peticion
    ) {
        TokenEmitido token = servicioAutenticacion.iniciarSesion(
            peticion.usuario(), peticion.clave());
        return new IniciarSesionRespuesta(
            token.token(), TIPO_TOKEN, token.expiraEn());
    }

    @GetMapping("/yo")
    UsuarioActualRespuesta usuarioActual(@AuthenticationPrincipal Jwt jwt) {
        Usuario usuario = servicioAutenticacion.obtenerActual(jwt.getSubject());
        List<String> roles = usuario.getRoles().stream()
            .filter(Rol::isActivo)
            .map(Rol::getCodigo)
            .sorted()
            .toList();

        return new UsuarioActualRespuesta(
            usuario.getId(),
            usuario.getUsuario(),
            usuario.getNombre(),
            roles,
            usuario.getPerfilRiesgo().getCodigo()
        );
    }
}
