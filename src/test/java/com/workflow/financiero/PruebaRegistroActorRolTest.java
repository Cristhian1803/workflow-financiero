package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.RegistroActorRolServicio;
import com.workflow.financiero.dominio.excepcion.RolAprobadorInvalidoException;
import com.workflow.financiero.dominio.excepcion.SegregacionFuncionesException;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.PerfilRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaRegistroActorRolTest extends PruebaIntegracionBase {

    @Autowired
    RegistroActorRolServicio servicio;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    SolicitudFinancieraRepositorio solicitudRepositorio;

    @Autowired
    PerfilRiesgoRepositorio perfilRiesgoRepositorio;

    @Autowired
    RolRepositorio rolRepositorio;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarDatos() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera, usuario CASCADE");
    }

    @Test
    @DisplayName("Permite repetir el mismo rol funcional en la misma solicitud")
    void permiteRepetirElMismoRol() {
        Usuario usuario = crearUsuario();
        SolicitudFinanciera solicitud = crearSolicitud(usuario);

        servicio.registrarAprobacion(solicitud.getId(), usuario.getId(), "SUPERVISOR");
        servicio.registrarAprobacion(solicitud.getId(), usuario.getId(), "SUPERVISOR");

        assertThat(contarRegistros(solicitud, usuario)).isEqualTo(1L);
        assertThat(rolRegistrado(solicitud, usuario)).isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("Rechaza un rol funcional distinto para el mismo actor y solicitud")
    void rechazaUnRolFuncionalDistinto() {
        Usuario usuario = crearUsuario();
        SolicitudFinanciera solicitud = crearSolicitud(usuario);

        servicio.registrarAprobacion(solicitud.getId(), usuario.getId(), "SUPERVISOR");

        assertThatThrownBy(() ->
            servicio.registrarAprobacion(solicitud.getId(), usuario.getId(), "GERENTE"))
            .isInstanceOf(SegregacionFuncionesException.class);

        assertThat(contarRegistros(solicitud, usuario)).isEqualTo(1L);
        assertThat(rolRegistrado(solicitud, usuario)).isEqualTo("SUPERVISOR");
    }

    @Test
    @DisplayName("Rechaza un rol que no es aprobador funcional")
    void rechazaUnRolQueNoEsAprobador() {
        Usuario usuario = crearUsuario();
        SolicitudFinanciera solicitud = crearSolicitud(usuario);

        assertThatThrownBy(() ->
            servicio.registrarAprobacion(solicitud.getId(), usuario.getId(), "SOLICITANTE"))
            .isInstanceOf(RolAprobadorInvalidoException.class);

        assertThat(contarRegistros(solicitud, usuario)).isZero();
    }

    @Test
    @DisplayName("Serializa dos roles distintos lanzados en concurrencia")
    void serializaDosRolesDistintosEnConcurrencia() throws Exception {
        Usuario usuario = crearUsuario();
        SolicitudFinanciera solicitud = crearSolicitud(usuario);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> supervisor = ejecutor.submit(
                intentarRegistrar(inicio, solicitud.getId(), usuario.getId(), "SUPERVISOR")
            );
            Future<Boolean> gerente = ejecutor.submit(
                intentarRegistrar(inicio, solicitud.getId(), usuario.getId(), "GERENTE")
            );

            inicio.countDown();

            boolean ganoSupervisor = supervisor.get(15, TimeUnit.SECONDS);
            boolean ganoGerente = gerente.get(15, TimeUnit.SECONDS);

            assertThat(ganoSupervisor ^ ganoGerente)
                .as("exactamente uno de los dos roles debe quedar registrado")
                .isTrue();
            assertThat(contarRegistros(solicitud, usuario)).isEqualTo(1L);
            assertThat(rolRegistrado(solicitud, usuario)).isIn("SUPERVISOR", "GERENTE");
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private Callable<Boolean> intentarRegistrar(
        CountDownLatch inicio,
        UUID solicitudId,
        UUID usuarioId,
        String rol
    ) {
        return () -> {
            inicio.await();
            try {
                servicio.registrarAprobacion(solicitudId, usuarioId, rol);
                return true;
            } catch (SegregacionFuncionesException ex) {
                return false;
            }
        };
    }

    private Usuario crearUsuario() {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById("NORMAL").orElseThrow();
        Rol rol = rolRepositorio.findById("SOLICITANTE").orElseThrow();
        return usuarioRepositorio.saveAndFlush(
            new Usuario(
                UUID.randomUUID(),
                "usuario-" + UUID.randomUUID(),
                "hash-de-prueba",
                "Usuario de prueba",
                perfil,
                Set.of(rol)
            )
        );
    }

    private SolicitudFinanciera crearSolicitud(Usuario usuario) {
        return solicitudRepositorio.saveAndFlush(new SolicitudFinanciera(usuario));
    }

    private Long contarRegistros(SolicitudFinanciera solicitud, Usuario usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? AND regiactorol_usua_id = ?",
            Long.class,
            solicitud.getId(),
            usuario.getId()
        );
    }

    private String rolRegistrado(SolicitudFinanciera solicitud, Usuario usuario) {
        return jdbcTemplate.queryForObject(
            "SELECT regiactorol_rol_codigo FROM registro_actor_rol "
                + "WHERE regiactorol_solifina_id = ? AND regiactorol_usua_id = ?",
            String.class,
            solicitud.getId(),
            usuario.getId()
        );
    }
}
