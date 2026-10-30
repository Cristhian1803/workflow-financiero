package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.excepcion.CredencialesInvalidasException;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import com.workflow.financiero.infraestructura.seguridad.PropiedadesJwt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autentica usuarios y emite tokens JWT con sus roles actuales.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutenticacion {

    private static final String PREFIJO_ROL = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final PropiedadesJwt propiedadesJwt;
    private final UsuarioRepositorio usuarioRepositorio;

    public TokenEmitido iniciarSesion(String usuario, String clave) {
        String normalizado = NormalizadorUsuario.normalizar(usuario);
        Authentication autenticacion;
        try {
            autenticacion = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizado, clave));
        } catch (BadCredentialsException | DisabledException | LockedException ex) {
            throw new CredencialesInvalidasException();
        }

        List<String> roles = autenticacion.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(autoridad -> autoridad != null && autoridad.startsWith(PREFIJO_ROL))
            .map(autoridad -> autoridad.substring(PREFIJO_ROL.length()))
            .sorted()
            .toList();

        Instant emitidoEn = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emitidoEn.plus(
            propiedadesJwt.expiracionMinutos(), ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(propiedadesJwt.emisor())
            .subject(autenticacion.getName())
            .issuedAt(emitidoEn)
            .expiresAt(expiraEn)
            .claim("roles", roles)
            .build();

        Jwt token = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            claims
        ));

        return new TokenEmitido(token.getTokenValue(), expiraEn);
    }

    @Transactional(readOnly = true)
    public Usuario obtenerActual(String nombreUsuario) {
        String normalizado = NormalizadorUsuario.normalizar(nombreUsuario);
        if (normalizado == null || normalizado.isBlank()) {
            throw new CredencialesInvalidasException();
        }
        return usuarioRepositorio.findByUsuario(normalizado)
            .orElseThrow(CredencialesInvalidasException::new);
    }
}
