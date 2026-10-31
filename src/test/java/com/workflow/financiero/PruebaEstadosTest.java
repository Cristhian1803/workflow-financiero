package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.EventoSolicitud;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
class PruebaEstadosTest extends PruebaIntegracionBase {

    private static final Map<EstadoRevision, Set<EstadoRevision>> TRANSICIONES = Map.of(
        EstadoRevision.RECIBIDA, Set.of(
            EstadoRevision.VALIDANDO,
            EstadoRevision.CANCELADA
        ),
        EstadoRevision.VALIDANDO, Set.of(
            EstadoRevision.EVALUANDO_RIESGO,
            EstadoRevision.CANCELADA
        ),
        EstadoRevision.EVALUANDO_RIESGO, Set.of(
            EstadoRevision.PENDIENTE_APROBACION,
            EstadoRevision.APROBADA,
            EstadoRevision.REVISION_MANUAL,
            EstadoRevision.CANCELADA
        ),
        EstadoRevision.PENDIENTE_APROBACION, Set.of(
            EstadoRevision.APROBADA,
            EstadoRevision.RECHAZADA,
            EstadoRevision.CANCELADA
        ),
        EstadoRevision.APROBADA, Set.of(
            EstadoRevision.EJECUTANDO,
            EstadoRevision.CANCELADA
        ),
        EstadoRevision.EJECUTANDO, Set.of(
            EstadoRevision.EJECUTADA,
            EstadoRevision.RESULTADO_DESCONOCIDO,
            EstadoRevision.COMPENSANDO,
            EstadoRevision.REVISION_MANUAL
        ),
        EstadoRevision.RESULTADO_DESCONOCIDO, Set.of(
            EstadoRevision.EJECUTADA,
            EstadoRevision.COMPENSANDO,
            EstadoRevision.REVISION_MANUAL
        ),
        EstadoRevision.COMPENSANDO, Set.of(
            EstadoRevision.CANCELADA,
            EstadoRevision.REVISION_MANUAL
        ),
        EstadoRevision.REVISION_MANUAL, Set.of(EstadoRevision.EVALUANDO_RIESGO)
    );

    @Autowired
    ServicioTransicionRevision servicio;

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
    @DisplayName("La máquina de estados define exactamente las transiciones permitidas")
    void laMaquinaDeEstadosDefineLasTransicionesPermitidas() {
        assertThat(TRANSICIONES.values().stream().mapToInt(Set::size).sum())
            .isEqualTo(23);

        TRANSICIONES.forEach((origen, destinos) -> destinos.forEach(destino ->
            assertThat(origen.puedeTransicionarA(destino))
                .as("%s debe poder pasar a %s", origen, destino)
                .isTrue()
        ));

        for (EstadoRevision origen : EstadoRevision.values()) {
            for (EstadoRevision destino : EstadoRevision.values()) {
                boolean esperada = TRANSICIONES.getOrDefault(origen, Set.of()).contains(destino);
                assertThat(origen.puedeTransicionarA(destino))
                    .as("transición %s -> %s", origen, destino)
                    .isEqualTo(esperada);
            }
        }

        assertThat(EstadoRevision.RECIBIDA.puedeTransicionarA(null)).isFalse();
    }

    @Test
    @DisplayName("Los estados terminales no tienen salidas")
    void losEstadosTerminalesNoTienenSalidas() {
        for (EstadoRevision estado : EstadoRevision.values()) {
            boolean terminal = estado == EstadoRevision.RECHAZADA
                || estado == EstadoRevision.CANCELADA
                || estado == EstadoRevision.EJECUTADA;

            assertThat(estado.esTerminal())
                .as("terminalidad de %s", estado)
                .isEqualTo(terminal);

            if (terminal) {
                for (EstadoRevision destino : EstadoRevision.values()) {
                    assertThat(estado.puedeTransicionarA(destino))
                        .as("%s no debe poder pasar a %s", estado, destino)
                        .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("Todas las transiciones permitidas se ejecutan por el servicio")
    void todasLasTransicionesPermitidasSeEjecutanPorElServicio() {
        TRANSICIONES.forEach((origen, destinos) -> destinos.forEach(destino -> {
            SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
            fijarEstado(creada.revision().getId(), origen);

            ContextoTransicion contexto = origen == EstadoRevision.REVISION_MANUAL
                ? new ContextoTransicion(null, null, "corr-" + origen, "detalle", true)
                : new ContextoTransicion(null, null, "corr-" + origen, "detalle");

            servicio.transicionar(
                creada.solicitud().getId(),
                creada.revision().getId(),
                destino,
                contexto
            );

            assertThat(estadoPersistido(creada.revision().getId()))
                .as("transición %s -> %s", origen, destino)
                .isEqualTo(destino.name());

            List<EventoSolicitud> eventos = eventoRepositorio
                .buscarPorRevision(creada.revision().getId());
            assertThat(eventos).hasSize(1);
            assertThat(eventos.getFirst().getTipo()).isEqualTo(TipoEventoSolicitud.CAMBIO_ESTADO);
            assertThat(eventos.getFirst().getEstadoAnterior()).isEqualTo(origen);
            assertThat(eventos.getFirst().getEstadoNuevo()).isEqualTo(destino);
        }));
    }

    @Test
    @DisplayName("Las transiciones inválidas no cambian el estado ni registran eventos")
    void lasTransicionesInvalidasNoCambianEstadoNiRegistranEventos() {
        List<CasoInvalido> casos = List.of(
            new CasoInvalido(EstadoRevision.RECIBIDA, EstadoRevision.EVALUANDO_RIESGO),
            new CasoInvalido(EstadoRevision.PENDIENTE_APROBACION, EstadoRevision.EJECUTANDO),
            new CasoInvalido(EstadoRevision.RECHAZADA, EstadoRevision.EJECUTADA),
            new CasoInvalido(EstadoRevision.CANCELADA, EstadoRevision.EJECUTADA),
            new CasoInvalido(EstadoRevision.EJECUTADA, EstadoRevision.EVALUANDO_RIESGO),
            new CasoInvalido(EstadoRevision.EVALUANDO_RIESGO, EstadoRevision.EVALUANDO_RIESGO)
        );

        casos.forEach(caso -> {
            SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
            fijarEstado(creada.revision().getId(), caso.origen());

            assertThatThrownBy(() -> servicio.transicionar(
                creada.solicitud().getId(),
                creada.revision().getId(),
                caso.destino(),
                new ContextoTransicion(null, null, "corr-invalida", "detalle")
            ))
                .as("transición %s -> %s", caso.origen(), caso.destino())
                .isInstanceOf(TransicionEstadoInvalidaException.class);

            assertThat(estadoPersistido(creada.revision().getId()))
                .isEqualTo(caso.origen().name());
            assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId()))
                .isEmpty();
        });
    }

    @Test
    @DisplayName("El estado nuevo nulo se rechaza sin cambiar la revisión")
    void elEstadoNuevoNuloSeRechaza() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));

        assertThatThrownBy(() -> servicio.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            null,
            new ContextoTransicion(null, null, "corr-nula", "detalle")
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoPersistido(creada.revision().getId())).isEqualTo("RECIBIDA");
        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId())).isEmpty();
    }

    @Test
    @DisplayName("El retorno desde revisión manual exige contexto de riesgo")
    void elRetornoDesdeRevisionManualExigeContextoDeRiesgo() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        fijarEstado(creada.revision().getId(), EstadoRevision.REVISION_MANUAL);

        assertThatThrownBy(() -> servicio.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(null, null, "corr-manual", "detalle")
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoPersistido(creada.revision().getId())).isEqualTo("REVISION_MANUAL");
        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId())).isEmpty();

        servicio.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(null, null, "corr-manual", "detalle", true)
        );

        assertThat(estadoPersistido(creada.revision().getId())).isEqualTo("EVALUANDO_RIESGO");
        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId())).hasSize(1);
    }

    @Test
    @DisplayName("Rechaza una revisión que no es la actual de la solicitud")
    void rechazaUnaRevisionQueNoEsLaActual() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        RevisionSolicitud segunda = agregarRevision(creada.solicitud(), 2);

        assertThatThrownBy(() -> servicio.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-vieja", "detalle")
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoPersistido(creada.revision().getId())).isEqualTo("RECIBIDA");
        assertThat(estadoPersistido(segunda.getId())).isEqualTo("RECIBIDA");
        assertThat(revisionActualDeLaSolicitud(creada.solicitud().getId()))
            .isEqualTo(segunda.getId().toString());
        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId())).isEmpty();
        assertThat(eventoRepositorio.buscarPorRevision(segunda.getId())).isEmpty();

        servicio.transicionar(
            creada.solicitud().getId(),
            segunda.getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-actual", "detalle")
        );

        assertThat(estadoPersistido(segunda.getId())).isEqualTo("VALIDANDO");
        assertThat(revisionActualDeLaSolicitud(creada.solicitud().getId()))
            .isEqualTo(segunda.getId().toString());
    }

    @Test
    @DisplayName("Rechaza una revisión que pertenece a otra solicitud")
    void rechazaUnaRevisionDeOtraSolicitud() {
        SolicitudCreada primera = crearSolicitud(crearUsuario("SOLICITANTE"));
        SolicitudCreada segunda = crearSolicitud(crearUsuario("SOLICITANTE"));

        assertThatThrownBy(() -> servicio.transicionar(
            primera.solicitud().getId(),
            segunda.revision().getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-ajena", "detalle")
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(estadoPersistido(segunda.revision().getId())).isEqualTo("RECIBIDA");
        assertThat(eventoRepositorio.buscarPorRevision(segunda.revision().getId())).isEmpty();
    }

    @Test
    @DisplayName("El estado de la solicitud coincide con su revisión actual")
    void elEstadoDeLaSolicitudCoincideConLaRevisionActual() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));

        servicio.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(null, null, "corr-actual", "detalle")
        );

        assertThat(revisionActualDeLaSolicitud(creada.solicitud().getId()))
            .isEqualTo(creada.revision().getId().toString());
        assertThat(estadoActualDeLaSolicitud(creada.solicitud().getId()))
            .isEqualTo("VALIDANDO");
    }

    @Test
    @DisplayName("El rollback revierte el estado y el evento")
    void elRollbackRevierteEstadoYEvento() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("SOLICITANTE"));
        TransactionTemplate plantilla = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> plantilla.executeWithoutResult(estado -> {
            servicio.transicionar(
                creada.solicitud().getId(),
                creada.revision().getId(),
                EstadoRevision.VALIDANDO,
                new ContextoTransicion(null, null, "corr-rollback", "detalle")
            );
            entityManager.flush();
            throw new ErrorSimuladoPrueba();
        })).isInstanceOf(ErrorSimuladoPrueba.class);

        assertThat(estadoPersistido(creada.revision().getId())).isEqualTo("RECIBIDA");
        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId())).isEmpty();
    }

    @Test
    @DisplayName("La revisión no expone un setter público de estado")
    void laRevisionNoExponeSetterDeEstado() {
        assertThat(Arrays.stream(RevisionSolicitud.class.getMethods())
            .filter(metodo -> metodo.getName().equals("setEstado")))
            .isEmpty();
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

    private void fijarEstado(UUID revisionId, EstadoRevision estado) {
        jdbcTemplate.update(
            "UPDATE revision_solicitud SET revisoli_estado = ? WHERE revisoli_id = ?",
            estado.name(),
            revisionId
        );
    }

    private String estadoPersistido(UUID revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_estado FROM revision_solicitud WHERE revisoli_id = ?",
            String.class,
            revisionId
        );
    }

    private String revisionActualDeLaSolicitud(UUID solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT solifina_revisoli_actual_id::text "
                + "FROM solicitud_financiera WHERE solifina_id = ?",
            String.class,
            solicitudId
        );
    }

    private String estadoActualDeLaSolicitud(UUID solicitudId) {
        return jdbcTemplate.queryForObject(
            "SELECT r.revisoli_estado FROM solicitud_financiera s "
                + "JOIN revision_solicitud r "
                + "ON r.revisoli_id = s.solifina_revisoli_actual_id "
                + "WHERE s.solifina_id = ?",
            String.class,
            solicitudId
        );
    }

    private record SolicitudCreada(
        Usuario usuario,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
    }

    private record CasoInvalido(EstadoRevision origen, EstadoRevision destino) {
    }
}
