package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.EventoSolicitud;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.Moneda;
import com.workflow.financiero.dominio.modelo.PerfilRiesgo;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.TipoOperacion;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.MonedaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.PerfilRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TipoOperacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.UsuarioRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaPersistenciaTest extends PruebaIntegracionBase {

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    SolicitudFinancieraRepositorio solicitudRepositorio;

    @Autowired
    RevisionSolicitudRepositorio revisionRepositorio;

    @Autowired
    EventoSolicitudRepositorio eventoRepositorio;

    @Autowired
    MonedaRepositorio monedaRepositorio;

    @Autowired
    TipoOperacionRepositorio tipoOperacionRepositorio;

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
    @DisplayName("Guarda y recupera solicitud, revisión, usuario y evento con sus tipos")
    void guardaYRecuperaSolicitudRevisionYEvento() {
        Usuario usuario = crearUsuario("usuario-" + UUID.randomUUID());
        SolicitudCreada creada = crearSolicitud(usuario);
        Rol rolSolicitante = rolRepositorio.findById("SOLICITANTE").orElseThrow();

        EventoSolicitud evento = new EventoSolicitud(
            creada.solicitud(),
            creada.revision(),
            TipoEventoSolicitud.SOLICITUD_CREADA,
            usuario,
            rolSolicitante,
            null,
            EstadoRevision.RECIBIDA,
            UUID.randomUUID().toString(),
            "Solicitud registrada"
        );
        eventoRepositorio.guardar(evento);

        Usuario usuarioCargado = usuarioRepositorio
            .findByUsuario(usuario.getUsuario())
            .orElseThrow();
        SolicitudFinanciera solicitudCargada = solicitudRepositorio
            .buscarDetallePorId(creada.solicitud().getId())
            .orElseThrow();
        RevisionSolicitud revisionCargada = revisionRepositorio
            .buscarDetallePorId(creada.revision().getId())
            .orElseThrow();
        List<EventoSolicitud> eventos = eventoRepositorio
            .buscarPorSolicitud(creada.solicitud().getId());

        assertThat(usuarioCargado.getRoles())
            .extracting(Rol::getCodigo)
            .containsExactly("SOLICITANTE");
        assertThat(usuarioCargado.getPerfilRiesgo().getCodigo()).isEqualTo("NORMAL");

        assertThat(solicitudCargada.getRevisionActual().getId())
            .isEqualTo(creada.revision().getId());
        assertThat(solicitudCargada.getSolicitante().getId()).isEqualTo(usuario.getId());
        assertThat(solicitudCargada.getCreadoEn()).isEqualTo(creada.solicitud().getCreadoEn());
        assertThat(solicitudCargada.getActualizadoEn())
            .isEqualTo(creada.solicitud().getActualizadoEn())
            .isNotNull();

        assertThat(revisionCargada.getEstado()).isEqualTo(EstadoRevision.RECIBIDA);
        assertThat(revisionCargada.getNivelRiesgo()).isNull();
        assertThat(revisionCargada.getMonto()).isEqualByComparingTo("25000000");
        assertThat(revisionCargada.getMonto().scale()).isZero();
        assertThat(revisionCargada.getTipoOperacion().getCodigo()).isEqualTo("NORMAL");
        assertThat(revisionCargada.getMoneda().getCodigo()).isEqualTo("PYG");
        assertThat(revisionCargada.getCreadoEn()).isEqualTo(creada.revision().getCreadoEn());
        assertThat(revisionCargada.getSolicitud().getSolicitante().getId())
            .isEqualTo(usuario.getId());

        assertThat(eventos).hasSize(1);
        EventoSolicitud eventoCargado = eventos.getFirst();
        assertThat(eventoCargado.getTipo()).isEqualTo(TipoEventoSolicitud.SOLICITUD_CREADA);
        assertThat(eventoCargado.getActor().getUsuario()).isEqualTo(usuario.getUsuario());
        assertThat(eventoCargado.getRol().getCodigo()).isEqualTo("SOLICITANTE");
        assertThat(eventoCargado.getEstadoAnterior()).isNull();
        assertThat(eventoCargado.getEstadoNuevo()).isEqualTo(EstadoRevision.RECIBIDA);
        assertThat(eventoCargado.getRevision().getNumero()).isEqualTo(1);
        assertThat(eventoCargado.getCreadoEn()).isEqualTo(evento.getCreadoEn());
    }

    @Test
    @DisplayName("No permite dos revisiones con el mismo número en una solicitud")
    void noPermiteDosRevisionesConElMismoNumero() {
        Usuario usuario = crearUsuario("usuario-" + UUID.randomUUID());
        SolicitudCreada creada = crearSolicitud(usuario);

        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();
        RevisionSolicitud duplicada = new RevisionSolicitud(
            creada.solicitud(),
            1,
            tipo,
            new BigDecimal("1000000"),
            moneda,
            "Otra descripción",
            "Otra justificación"
        );

        assertThatThrownBy(() -> revisionRepositorio.saveAndFlush(duplicada))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una actualización con una versión anterior falla por bloqueo optimista")
    void unaVersionAnteriorFallaPorBloqueoOptimista() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID solicitudId = creada.solicitud().getId();

        SolicitudFinanciera copiaActual = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow();
        SolicitudFinanciera copiaObsoleta = solicitudRepositorio
            .buscarDetallePorId(solicitudId)
            .orElseThrow();

        copiaActual.establecerRevisionActual(copiaActual.getRevisionActual());
        solicitudRepositorio.saveAndFlush(copiaActual);

        copiaObsoleta.establecerRevisionActual(copiaObsoleta.getRevisionActual());

        assertThatThrownBy(() -> solicitudRepositorio.saveAndFlush(copiaObsoleta))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("La revisión actual debe pertenecer a la misma solicitud")
    void laRevisionActualDebePertenecerALaMismaSolicitud() {
        SolicitudCreada primera = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        Usuario otroUsuario = crearUsuario("usuario-" + UUID.randomUUID());
        SolicitudFinanciera segunda = solicitudRepositorio.saveAndFlush(
            new SolicitudFinanciera(otroUsuario)
        );
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();
        RevisionSolicitud revisionAjena = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                segunda,
                1,
                tipo,
                new BigDecimal("1000000"),
                moneda,
                "Otra descripción",
                "Otra justificación"
            )
        );

        primera.solicitud().establecerRevisionActual(revisionAjena);

        assertThatThrownBy(() -> solicitudRepositorio.saveAndFlush(primera.solicitud()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Usuario crearUsuario(String nombreUsuario) {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById("NORMAL").orElseThrow();
        Rol rol = rolRepositorio.findById("SOLICITANTE").orElseThrow();
        Usuario usuario = new Usuario(
            UUID.randomUUID(),
            nombreUsuario,
            "hash-de-prueba",
            "Usuario de prueba",
            perfil,
            Set.of(rol)
        );
        return usuarioRepositorio.saveAndFlush(usuario);
    }

    private SolicitudCreada crearSolicitud(Usuario usuario) {
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        SolicitudFinanciera solicitud = solicitudRepositorio.saveAndFlush(
            new SolicitudFinanciera(usuario)
        );
        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                1,
                tipo,
                new BigDecimal("25000000"),
                moneda,
                "Pago de demostración",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitud = solicitudRepositorio.saveAndFlush(solicitud);

        return new SolicitudCreada(usuario, solicitud, revision);
    }

    private record SolicitudCreada(
        Usuario usuario,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
    }
}
