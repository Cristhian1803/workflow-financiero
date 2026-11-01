package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.excepcion.CredencialesInvalidasException;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el usuario autenticado y comprueba que siga activo. Un token
 * vigente no alcanza si el usuario fue desactivado después de emitirlo.
 */
@Service
@RequiredArgsConstructor
public class ServicioUsuarioActual {

    private final UsuarioRepositorio usuarioRepositorio;

    @Transactional(readOnly = true)
    public UsuarioActual obtenerActual(String nombreUsuario) {
        String normalizado = NormalizadorUsuario.normalizar(nombreUsuario);
        if (normalizado == null || normalizado.isBlank()) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarioRepositorio.findByUsuario(normalizado)
            .orElseThrow(CredencialesInvalidasException::new);
        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException();
        }

        List<String> roles = usuario.getRoles().stream()
            .filter(Rol::isActivo)
            .map(Rol::getCodigo)
            .sorted()
            .toList();

        return new UsuarioActual(
            usuario.getId(),
            usuario.getUsuario(),
            usuario.getNombre(),
            roles,
            usuario.getPerfilRiesgo().getCodigo()
        );
    }
}
