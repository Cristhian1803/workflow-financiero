package com.workflow.financiero.infraestructura.seguridad;

import com.workflow.financiero.aplicacion.servicio.NormalizadorUsuario;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga desde PostgreSQL el usuario que intenta autenticarse.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetallesService implements UserDetailsService {

    private final UsuarioRepositorio usuarioRepositorio;

    @Override
    public UserDetails loadUserByUsername(String nombreUsuario) {
        String normalizado = NormalizadorUsuario.normalizar(nombreUsuario);
        if (normalizado == null || normalizado.isBlank()) {
            throw new UsernameNotFoundException("Usuario o clave inválidos");
        }

        Usuario usuario = usuarioRepositorio.findByUsuario(normalizado)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario o clave inválidos"));

        List<GrantedAuthority> autoridades = usuario.getRoles().stream()
            .filter(Rol::isActivo)
            .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(
                "ROLE_" + rol.getCodigo()))
            .toList();

        return User.withUsername(usuario.getUsuario())
            .password(usuario.getClaveHash())
            .authorities(autoridades)
            .disabled(!usuario.isActivo())
            .build();
    }
}
