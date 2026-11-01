package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.ServicioEventoSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.EventoSolicitud;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "flowable.async-executor-activate=false",
    "spring.profiles.active=pruebas"
})
class PruebaHistorialTest extends PruebaIntegracionBase {

    @Autowired
    ServicioTransicionRevision servicioTransicion;

    @Autowired
    ServicioEventoSolicitud servicioEvento;

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

    @Autowired
    PlatformTransactionManager transactionManager;

    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void limpiarDatos() {
        jdbcTemplate.execute("TRUNCATE TABLE solicitud_financiera, usuario CASCADE");
    }

    @Test
    @DisplayName("El historial se ordena por fecha y desempata por identificador")
    void elHistorialSeOrdenaPorFechaYDesempataPorIdentificador() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        Usuario supervisor = crearUsuario("SUPERVISOR");

        transicionar(creada, EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-1", "Paso 1"));
        transicionar(creada, EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(supervisor.getId(), "SUPERVISOR", "corr-2", "Paso 2"));
        transicionar(creada, EstadoRevision.PENDIENTE_APROBACION,
            new ContextoTransicion(null, null, "corr-3", "Paso 3"));

        List<EventoSolicitud> eventos = eventoRepositorio
            .buscarPorSolicitud(creada.solicitud().getId());

        assertThat(eventos)
            .extracting(EventoSolicitud::getId)
            .containsExactlyElementsOf(idsOrdenadosPorSql(creada.solicitud().getId()));
        assertThat(eventos)
            .extracting(EventoSolicitud::getCreadoEn)
            .isSorted();

        Instant instanteComun = Instant.now()
            .plusSeconds(60)
            .truncatedTo(ChronoUnit.MICROS);
        insertarEventoConFecha(creada.solicitud().getId(), creada.revision().getId(), instanteComun);
        insertarEventoConFecha(creada.solicitud().getId(), creada.revision().getId(), instanteComun);

        List<EventoSolicitud> conEmpate = eventoRepositorio
            .buscarPorSolicitud(creada.solicitud().getId());

        assertThat(conEmpate)
            .extracting(EventoSolicitud::getId)
            .containsExactlyElementsOf(idsOrdenadosPorSql(creada.solicitud().getId()));
        assertThat(conEmpate)
            .extracting(EventoSolicitud::getCreadoEn)
            .isSorted();
    }

    @Test
    @DisplayName("El evento guarda actor, rol, estados y correlación")
    void elEventoGuardaActorRolEstadosYCorrelacion() {
        Usuario solicitante = crearUsuario("SOLICITANTE");
        Usuario supervisor = crearUsuario("SUPERVISOR");
        SolicitudCreada creada = crearSolicitud(solicitante);

        transicionar(creada, EstadoRevision.VALIDANDO,
            new ContextoTransicion(
                supervisor.getId(),
                "SUPERVISOR",
                "corr-actor",
                "Transición con actor"
            ));

        EventoSolicitud evento = eventoRepositorio
            .buscarPorRevision(creada.revision().getId())
            .getFirst();

        assertThat(evento.getTipo()).isEqualTo(TipoEventoSolicitud.CAMBIO_ESTADO);
        assertThat(evento.getSolicitud().getId()).isEqualTo(creada.solicitud().getId());
        assertThat(evento.getRevision().getId()).isEqualTo(creada.revision().getId());
        assertThat(evento.getActor().getId()).isEqualTo(supervisor.getId());
        assertThat(evento.getRol().getCodigo()).isEqualTo("SUPERVISOR");
        assertThat(evento.getEstadoAnterior()).isEqualTo(EstadoRevision.RECIBIDA);
        assertThat(evento.getEstadoNuevo()).isEqualTo(EstadoRevision.VALIDANDO);
        assertThat(evento.getCorrelacion()).isEqualTo("corr-actor");
        assertThat(evento.getDetalle()).isEqualTo("Transición con actor");
        assertThat(evento.getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Los eventos de sistema no tienen actor ni rol")
    void losEventosDeSistemaNoTienenActorNiRol() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));

        transicionar(creada, EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-sistema", "Transición de sistema"));

        EventoSolicitud evento = eventoRepositorio
            .buscarPorRevision(creada.revision().getId())
            .getFirst();

        assertThat(evento.getActor()).isNull();
        assertThat(evento.getRol()).isNull();
        assertThat(evento.getEstadoAnterior()).isEqualTo(EstadoRevision.RECIBIDA);
        assertThat(evento.getEstadoNuevo()).isEqualTo(EstadoRevision.VALIDANDO);
    }

    @Test
    @DisplayName("El evento de creación se registra con estados nulos")
    void elEventoDeCreacionSeRegistraConEstadosNulos() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));

        servicioEvento.registrar(
            creada.solicitud(),
            creada.revision(),
            TipoEventoSolicitud.SOLICITUD_CREADA,
            null,
            null,
            null,
            null,
            "corr-alta",
            "Solicitud registrada"
        );

        EventoSolicitud evento = eventoRepositorio
            .buscarPorSolicitud(creada.solicitud().getId())
            .getFirst();

        assertThat(evento.getTipo()).isEqualTo(TipoEventoSolicitud.SOLICITUD_CREADA);
        assertThat(evento.getActor()).isNull();
        assertThat(evento.getRol()).isNull();
        assertThat(evento.getEstadoAnterior()).isNull();
        assertThat(evento.getEstadoNuevo()).isNull();
        assertThat(evento.getCorrelacion()).isEqualTo("corr-alta");
        assertThat(evento.getDetalle()).isEqualTo("Solicitud registrada");
    }

    @Test
    @DisplayName("El historial de una solicitud no incluye eventos de otra")
    void elHistorialDeUnaSolicitudNoIncluyeEventosDeOtra() {
        SolicitudCreada primera = crearSolicitud(crearUsuario("SOLICITANTE"));
        SolicitudCreada segunda = crearSolicitud(crearUsuario("SOLICITANTE"));

        transicionar(primera, EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-primera", "Primera"));
        transicionar(segunda, EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-segunda", "Segunda"));

        List<EventoSolicitud> eventosPrimera = eventoRepositorio
            .buscarPorSolicitud(primera.solicitud().getId());
        List<EventoSolicitud> eventosSegunda = eventoRepositorio
            .buscarPorSolicitud(segunda.solicitud().getId());

        assertThat(eventosPrimera).hasSize(1);
        assertThat(eventosPrimera.getFirst().getCorrelacion()).isEqualTo("corr-primera");
        assertThat(eventosSegunda).hasSize(1);
        assertThat(eventosSegunda.getFirst().getCorrelacion()).isEqualTo("corr-segunda");
    }

    @Test
    @DisplayName("El historial se puede consultar por revisión")
    void elHistorialSePuedeConsultarPorRevision() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        transicionar(creada, EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-revision-1", "Primera revisión"));

        RevisionSolicitud segunda = agregarRevision(creada.solicitud(), 2);
        servicioTransicion.transicionar(
            creada.solicitud().getId(),
            segunda.getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-revision-2", "Segunda revisión")
        );

        assertThat(eventoRepositorio.buscarPorSolicitud(creada.solicitud().getId()))
            .hasSize(2);

        List<EventoSolicitud> primeraRevision = eventoRepositorio
            .buscarPorRevision(creada.revision().getId());
        List<EventoSolicitud> segundaRevision = eventoRepositorio
            .buscarPorRevision(segunda.getId());

        assertThat(primeraRevision).hasSize(1);
        assertThat(primeraRevision.getFirst().getRevision().getId())
            .isEqualTo(creada.revision().getId());
        assertThat(primeraRevision.getFirst().getCorrelacion())
            .isEqualTo("corr-revision-1");

        assertThat(segundaRevision).hasSize(1);
        assertThat(segundaRevision.getFirst().getRevision().getId())
            .isEqualTo(segunda.getId());
        assertThat(segundaRevision.getFirst().getCorrelacion())
            .isEqualTo("corr-revision-2");
    }

    @Test
    @DisplayName("El rollback revierte el estado y el evento del historial")
    void elRollbackRevierteEstadoYEventoDelHistorial() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        TransactionTemplate plantilla = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> plantilla.executeWithoutResult(estado -> {
            transicionar(creada, EstadoRevision.VALIDANDO,
                new ContextoTransicion(null, null, "corr-rollback", "Rollback"));
            entityManager.flush();
            throw new ErrorSimuladoPrueba();
        })).isInstanceOf(ErrorSimuladoPrueba.class);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT revisoli_estado FROM revision_solicitud WHERE revisoli_id = ?",
            String.class,
            creada.revision().getId()
        )).isEqualTo("RECIBIDA");
        assertThat(eventoRepositorio.buscarPorSolicitud(creada.solicitud().getId()))
            .isEmpty();
    }

    @Test
    @DisplayName("El evento y su repositorio no exponen modificación ni borrado")
    void elEventoYSuRepositorioNoExponenModificacionNiBorrado() {
        assertThat(Arrays.stream(EventoSolicitud.class.getMethods())
            .filter(metodo -> metodo.getName().startsWith("set")))
            .isEmpty();

        Set<String> metodos = Arrays.stream(EventoSolicitudRepositorio.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(metodos).containsExactlyInAnyOrder(
            "guardar",
            "buscarPorSolicitud",
            "buscarPorRevision"
        );
    }

    private void transicionar(
        SolicitudCreada creada,
        EstadoRevision destino,
        ContextoTransicion contexto
    ) {
        servicioTransicion.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            destino,
            contexto
        );
    }

    private Usuario crearUsuario(String rolCodigo) {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById("NORMAL").orElseThrow();
        Rol rol = rolRepositorio.findById(rolCodigo).orElseThrow();
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

    private RevisionSolicitud agregarRevision(SolicitudFinanciera solicitud, int numero) {
        TipoOperacion tipo = tipoOperacionRepositorio.findById("NORMAL").orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                numero,
                tipo,
                new BigDecimal("25000000"),
                moneda,
                "Pago de demostración",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);
        return revision;
    }

    private List<UUID> idsOrdenadosPorSql(UUID solicitudId) {
        return jdbcTemplate.queryForList(
                "SELECT evensoli_id FROM evento_solicitud "
                    + "WHERE evensoli_solifina_id = ? "
                    + "ORDER BY evensoli_creado_en ASC, evensoli_id ASC",
                UUID.class,
                solicitudId
            );
    }

    private UUID insertarEventoConFecha(
        UUID solicitudId,
        UUID revisionId,
        Instant creadoEn
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO evento_solicitud ("
                + "evensoli_id, evensoli_solifina_id, evensoli_revisoli_id, "
                + "evensoli_tipo, evensoli_creado_en) "
                + "VALUES (?, ?, ?, 'CAMBIO_ESTADO', ?)",
            id,
            solicitudId,
            revisionId,
            OffsetDateTime.ofInstant(creadoEn, ZoneOffset.UTC)
        );
        return id;
    }

    private record SolicitudCreada(
        Usuario usuario,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
    }
}
