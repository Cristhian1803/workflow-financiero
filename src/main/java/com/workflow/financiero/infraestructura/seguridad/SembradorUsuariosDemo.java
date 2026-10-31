package com.workflow.financiero.infraestructura.seguridad;

import com.workflow.financiero.aplicacion.servicio.NormalizadorUsuario;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.PerfilRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea los usuarios de demostración en desarrollo y pruebas. No pisa
 * contraseñas existentes para que el arranque sea idempotente.
 */
@Component
@Profile({"desarrollo", "pruebas"})
@Slf4j
public class SembradorUsuariosDemo implements ApplicationRunner {

    private static final List<UsuarioDemo> USUARIOS = List.of(
        new UsuarioDemo("demo", "Usuario Demostración", "SOLICITANTE"),
        new UsuarioDemo("demo2", "Usuario Demostración 2", "SOLICITANTE"),
        new UsuarioDemo("auditor", "Auditor", "AUDITOR"),
        new UsuarioDemo("admin", "Administrador", "ADMINISTRADOR"),
        new UsuarioDemo("supervisor", "Supervisor", "SUPERVISOR"),
        new UsuarioDemo("gerente", "Gerente", "GERENTE"),
        new UsuarioDemo("riesgos", "Riesgos", "RIESGOS"),
        new UsuarioDemo("gerente_general", "Gerente general", "GERENTE_GENERAL"),
        new UsuarioDemo("jefe_riesgos", "Jefe de riesgos", "JEFE_RIESGOS"),
        new UsuarioDemo("operaciones", "Operaciones", "OPERACIONES")
    );

    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final PerfilRiesgoRepositorio perfilRiesgoRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final String clave;

    public SembradorUsuariosDemo(
        UsuarioRepositorio usuarioRepositorio,
        RolRepositorio rolRepositorio,
        PerfilRiesgoRepositorio perfilRiesgoRepositorio,
        PasswordEncoder passwordEncoder,
        @Value("${DEMO_CLAVE:demo}") String clave
    ) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("DEMO_CLAVE no puede estar vacía");
        }
        this.usuarioRepositorio = usuarioRepositorio;
        this.rolRepositorio = rolRepositorio;
        this.perfilRiesgoRepositorio = perfilRiesgoRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.clave = clave;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sembrar();
    }

    @Transactional
    public void sembrar() {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById("NORMAL")
            .orElseThrow(() -> new IllegalStateException(
                "No existe el perfil de riesgo NORMAL"));

        int creados = 0;
        for (UsuarioDemo demo : USUARIOS) {
            String usuario = NormalizadorUsuario.normalizar(demo.usuario());
            if (usuarioRepositorio.existsByUsuario(usuario)) {
                continue;
            }

            Rol rol = rolRepositorio.findById(demo.rol())
                .orElseThrow(() -> new IllegalStateException(
                    "No existe el rol " + demo.rol()));

            usuarioRepositorio.save(new Usuario(
                UUID.randomUUID(),
                usuario,
                passwordEncoder.encode(clave),
                demo.nombre(),
                perfil,
                Set.of(rol)
            ));
            creados++;
        }

        if (creados > 0) {
            log.info("Usuarios demo creados en este arranque: {}", creados);
        }
    }

    private record UsuarioDemo(String usuario, String nombre, String rol) {
    }
}
