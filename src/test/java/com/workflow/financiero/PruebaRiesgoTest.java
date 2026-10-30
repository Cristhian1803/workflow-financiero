package com.workflow.financiero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workflow.financiero.aplicacion.servicio.ServicioClasificacionRiesgo;
import com.workflow.financiero.aplicacion.servicio.ServicioTransicionRevision;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
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
import com.workflow.financiero.dominio.regla.riesgo.ClasificadorRiesgoInterno;
import com.workflow.financiero.dominio.regla.riesgo.ConsolidadorRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
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
import java.util.List;
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
class PruebaRiesgoTest extends PruebaIntegracionBase {

    private final ClasificadorRiesgoInterno clasificador = new ClasificadorRiesgoInterno();
    private final ConsolidadorRiesgo consolidador = new ConsolidadorRiesgo();

    @Autowired
    ServicioClasificacionRiesgo servicioRiesgo;

    @Autowired
    ServicioTransicionRevision servicioTransicion;

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
    @DisplayName("Clasifica los bordes de monto según la política interna")
    void clasificaLosBordesDeMonto() {
        List<CasoBorde> casos = List.of(
            new CasoBorde("9999999", NivelRiesgo.BAJO, false),
            new CasoBorde("10000000", NivelRiesgo.BAJO, false),
            new CasoBorde("10000001", NivelRiesgo.BAJO, false),
            new CasoBorde("49999999", NivelRiesgo.BAJO, false),
            new CasoBorde("50000000", NivelRiesgo.BAJO, false),
            new CasoBorde("50000001", NivelRiesgo.MEDIO, false),
            new CasoBorde("199999999", NivelRiesgo.MEDIO, false),
            new CasoBorde("200000000", NivelRiesgo.MEDIO, false),
            new CasoBorde("200000001", NivelRiesgo.ALTO, true)
        );

        for (CasoBorde caso : casos) {
            ResultadoRiesgo resultado = clasificar(caso.monto(), false, "NORMAL");

            assertThat(resultado.nivel())
                .as("nivel para monto %s", caso.monto())
                .isEqualTo(caso.nivelEsperado());
            assertThat(resultado.origen()).isEqualTo(OrigenRiesgo.INTERNO);
            assertThat(resultado.requiereEvaluacionExterna())
                .as("evaluación externa para monto %s", caso.monto())
                .isEqualTo(caso.requiereExterna());
            assertThat(resultado.requiereRevisionManual()).isFalse();
            assertThat(resultado.detalle()).contains("Política interna de demostración");
        }
    }

    @Test
    @DisplayName("El tipo sensible y el perfil alto elevan el riesgo a ALTO")
    void elTipoSensibleYElPerfilAltoElevanElRiesgo() {
        ResultadoRiesgo normal = clasificar("1000000", false, "NORMAL");
        assertThat(normal.nivel()).isEqualTo(NivelRiesgo.BAJO);

        ResultadoRiesgo sensible = clasificar("1000000", true, "NORMAL");
        assertThat(sensible.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(sensible.detalle()).contains("tipo de operación sensible");

        ResultadoRiesgo perfilAlto = clasificar("1000000", false, "ALTO");
        assertThat(perfilAlto.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(perfilAlto.detalle()).contains("perfil de riesgo del solicitante");

        ResultadoRiesgo medioSensible = clasificar("50000001", true, "NORMAL");
        assertThat(medioSensible.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(medioSensible.detalle()).contains("base MEDIO");
        assertThat(medioSensible.detalle()).contains("tipo de operación sensible");
    }

    @Test
    @DisplayName("La consolidación toma el mayor nivel entre interno y externo")
    void laConsolidacionTomaElMayorNivel() {
        ResultadoRiesgo internoAlto = clasificar("200000001", false, "NORMAL");

        ResultadoRiesgo externoInferior = consolidador.consolidar(
            internoAlto, NivelRiesgo.MEDIO);
        assertThat(externoInferior.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(externoInferior.origen()).isEqualTo(OrigenRiesgo.INTERNO);
        assertThat(externoInferior.requiereRevisionManual()).isFalse();

        ResultadoRiesgo externoIgual = consolidador.consolidar(
            internoAlto, NivelRiesgo.ALTO);
        assertThat(externoIgual.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(externoIgual.origen()).isEqualTo(OrigenRiesgo.INTERNO);
        assertThat(externoIgual.requiereRevisionManual()).isFalse();

        // Caso puro: en la política real todo monto con externo ya es ALTO.
        ResultadoRiesgo internoMedio = new ResultadoRiesgo(
            NivelRiesgo.MEDIO,
            OrigenRiesgo.INTERNO,
            true,
            false,
            "Resultado interno sintético"
        );
        ResultadoRiesgo externoSuperior = consolidador.consolidar(
            internoMedio, NivelRiesgo.ALTO);
        assertThat(externoSuperior.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(externoSuperior.origen()).isEqualTo(OrigenRiesgo.EXTERNO);
        assertThat(externoSuperior.requiereRevisionManual()).isFalse();

        ResultadoRiesgo internoBajo = clasificar("50000000", false, "NORMAL");
        ResultadoRiesgo sinExternoRequerido = consolidador.consolidar(
            internoBajo, NivelRiesgo.ALTO);
        assertThat(sinExternoRequerido.nivel()).isEqualTo(NivelRiesgo.BAJO);
    }

    @Test
    @DisplayName("Un externo ausente o inválido no autoriza la operación")
    void unExternoAusenteOInvalidoNoAutoriza() {
        ResultadoRiesgo interno = clasificar("200000001", false, "NORMAL");

        ResultadoRiesgo ausente = consolidador.consolidar(interno, null);

        assertThat(ausente.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(ausente.origen()).isEqualTo(OrigenRiesgo.INTERNO);
        assertThat(ausente.requiereEvaluacionExterna()).isTrue();
        assertThat(ausente.requiereRevisionManual()).isTrue();
        assertThat(ausente.detalle()).contains("no autoriza");
    }

    @Test
    @DisplayName("El consolidado nunca queda por debajo del riesgo interno")
    void elConsolidadoNuncaEsInferiorAlInterno() {
        for (NivelRiesgo interno : NivelRiesgo.values()) {
            ResultadoRiesgo sintetico = new ResultadoRiesgo(
                interno,
                OrigenRiesgo.INTERNO,
                true,
                false,
                "Resultado interno sintético"
            );

            for (NivelRiesgo externo : NivelRiesgo.values()) {
                ResultadoRiesgo consolidado = consolidador.consolidar(sintetico, externo);
                assertThat(consolidado.nivel().compareTo(interno))
                    .as("interno %s, externo %s", interno, externo)
                    .isGreaterThanOrEqualTo(0);
            }

            ResultadoRiesgo sinExterno = consolidador.consolidar(sintetico, null);
            assertThat(sinExterno.nivel()).isEqualTo(interno);
            assertThat(sinExterno.requiereRevisionManual()).isTrue();
        }
    }

    @Test
    @DisplayName("El nivel mayor respeta el orden de severidad")
    void elNivelMayorRespetaElOrdenDeSeveridad() {
        assertThat(NivelRiesgo.BAJO.mayor(NivelRiesgo.MEDIO)).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(NivelRiesgo.ALTO.mayor(NivelRiesgo.BAJO)).isEqualTo(NivelRiesgo.ALTO);
        assertThat(NivelRiesgo.MEDIO.mayor(NivelRiesgo.MEDIO)).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    @DisplayName("El dominio rechaza el nivel nulo y los estados no permitidos")
    void elDominioRechazaNivelNuloYEstadosNoPermitidos() {
        PerfilRiesgo perfil = new PerfilRiesgo("NORMAL", "Perfil normal", true);
        Usuario usuario = new Usuario(
            UUID.randomUUID(),
            "usuario-prueba",
            "hash-de-prueba",
            "Usuario de prueba",
            perfil,
            Set.of()
        );
        TipoOperacion tipo = new TipoOperacion("NORMAL", "Operación normal", false, true);
        Moneda moneda = new Moneda("PYG", "Guaraní paraguayo", "Gs.", (short) 0, true);
        SolicitudFinanciera solicitud = new SolicitudFinanciera(usuario);
        RevisionSolicitud revision = new RevisionSolicitud(
            solicitud,
            1,
            tipo,
            new BigDecimal("1000000"),
            moneda,
            "Pago de demostración",
            "Operación autorizada para pruebas"
        );

        assertThatThrownBy(() -> revision.registrarNivelRiesgo(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revision.registrarNivelRiesgo(NivelRiesgo.BAJO))
            .isInstanceOf(TransicionEstadoInvalidaException.class);
        assertThat(revision.getNivelRiesgo()).isNull();
    }

    @Test
    @DisplayName("Clasifica y registra el riesgo de la revisión actual")
    void clasificaYRegistraElRiesgo() {
        Usuario usuario = crearUsuario("NORMAL");
        SolicitudCreada creada = crearSolicitud(usuario, "NORMAL", "100000000");
        transicionar(creada, EstadoRevision.VALIDANDO);
        transicionar(creada, EstadoRevision.EVALUANDO_RIESGO);

        ResultadoRiesgo resultado = servicioRiesgo.clasificarYRegistrar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            "corr-riesgo"
        );

        assertThat(resultado.nivel()).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(resultado.requiereEvaluacionExterna()).isFalse();
        assertThat(nivelPersistido(creada.revision().getId())).isEqualTo("MEDIO");

        List<EventoSolicitud> eventos = eventoRepositorio
            .buscarPorRevision(creada.revision().getId());
        assertThat(eventos).hasSize(3);

        EventoSolicitud eventoRiesgo = eventos.stream()
            .filter(evento -> evento.getTipo() == TipoEventoSolicitud.RIESGO_CLASIFICADO)
            .findFirst()
            .orElseThrow();

        assertThat(eventoRiesgo.getActor()).isNull();
        assertThat(eventoRiesgo.getRol()).isNull();
        assertThat(eventoRiesgo.getEstadoAnterior()).isNull();
        assertThat(eventoRiesgo.getEstadoNuevo()).isNull();
        assertThat(eventoRiesgo.getCorrelacion()).isEqualTo("corr-riesgo");
        assertThat(eventoRiesgo.getDetalle())
            .contains("Política interna de demostración", "MEDIO");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evaluacion_riesgo", Long.class)).isZero();
    }

    @Test
    @DisplayName("Permite clasificar el riesgo mientras la revisión está en VALIDANDO")
    void permiteClasificarEnValidando() {
        Usuario usuario = crearUsuario("NORMAL");
        SolicitudCreada creada = crearSolicitud(usuario, "NORMAL", "1000000");
        transicionar(creada, EstadoRevision.VALIDANDO);

        ResultadoRiesgo resultado = servicioRiesgo.clasificarYRegistrar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            "corr-validando"
        );

        assertThat(resultado.nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(nivelPersistido(creada.revision().getId())).isEqualTo("BAJO");
    }

    @Test
    @DisplayName("Usa el perfil persistido del solicitante, no un dato del cliente")
    void usaElPerfilPersistidoDelSolicitante() {
        Usuario usuario = crearUsuario("ALTO");
        SolicitudCreada creada = crearSolicitud(usuario, "NORMAL", "1000000");
        transicionar(creada, EstadoRevision.VALIDANDO);
        transicionar(creada, EstadoRevision.EVALUANDO_RIESGO);

        ResultadoRiesgo resultado = servicioRiesgo.clasificarYRegistrar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            "corr-perfil"
        );

        assertThat(resultado.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(resultado.detalle()).contains("perfil de riesgo del solicitante");
        assertThat(nivelPersistido(creada.revision().getId())).isEqualTo("ALTO");
    }

    @Test
    @DisplayName("Marca la evaluación externa para montos mayores a 200 millones")
    void marcaLaEvaluacionExternaParaMontosMayores() {
        Usuario usuario = crearUsuario("NORMAL");
        SolicitudCreada creada = crearSolicitud(usuario, "NORMAL", "250000000");
        transicionar(creada, EstadoRevision.VALIDANDO);
        transicionar(creada, EstadoRevision.EVALUANDO_RIESGO);

        ResultadoRiesgo resultado = servicioRiesgo.clasificarYRegistrar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            "corr-externa"
        );

        assertThat(resultado.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(resultado.requiereEvaluacionExterna()).isTrue();
        assertThat(resultado.requiereRevisionManual()).isFalse();
        assertThat(resultado.detalle()).contains("evaluación externa requerida");

        assertThat(eventoRepositorio.buscarPorRevision(creada.revision().getId()))
            .extracting(EventoSolicitud::getTipo)
            .doesNotContain(TipoEventoSolicitud.EVALUACION_EXTERNA_PENDIENTE);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evaluacion_riesgo", Long.class)).isZero();
    }

    @Test
    @DisplayName("Rechaza clasificar una revisión que no está habilitada o no es la actual")
    void rechazaRevisionNoHabilitadaONoActual() {
        Usuario usuario = crearUsuario("NORMAL");

        SolicitudCreada enRecibida = crearSolicitud(usuario, "NORMAL", "1000000");
        assertThatThrownBy(() -> servicioRiesgo.clasificarYRegistrar(
            enRecibida.solicitud().getId(),
            enRecibida.revision().getId(),
            "corr-recibida"
        )).isInstanceOf(TransicionEstadoInvalidaException.class);
        assertThat(nivelPersistido(enRecibida.revision().getId())).isNull();
        assertThat(eventosDeRiesgo(enRecibida.revision().getId())).isEmpty();

        SolicitudCreada conNuevaRevision = crearSolicitud(usuario, "NORMAL", "1000000");
        RevisionSolicitud segunda = agregarRevision(conNuevaRevision.solicitud(), 2);

        assertThatThrownBy(() -> servicioRiesgo.clasificarYRegistrar(
            conNuevaRevision.solicitud().getId(),
            conNuevaRevision.revision().getId(),
            "corr-vieja"
        )).isInstanceOf(TransicionEstadoInvalidaException.class);

        assertThat(nivelPersistido(conNuevaRevision.revision().getId())).isNull();
        assertThat(nivelPersistido(segunda.getId())).isNull();
        assertThat(eventosDeRiesgo(conNuevaRevision.revision().getId())).isEmpty();
        assertThat(eventosDeRiesgo(segunda.getId())).isEmpty();
    }

    @Test
    @DisplayName("El rollback revierte el nivel y el evento de riesgo")
    void elRollbackRevierteNivelYEventoDeRiesgo() {
        Usuario usuario = crearUsuario("NORMAL");
        SolicitudCreada creada = crearSolicitud(usuario, "NORMAL", "100000000");
        transicionar(creada, EstadoRevision.VALIDANDO);
        transicionar(creada, EstadoRevision.EVALUANDO_RIESGO);

        TransactionTemplate plantilla = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> plantilla.executeWithoutResult(estado -> {
            servicioRiesgo.clasificarYRegistrar(
                creada.solicitud().getId(),
                creada.revision().getId(),
                "corr-rollback"
            );
            entityManager.flush();
            throw new ErrorSimuladoPrueba();
        })).isInstanceOf(ErrorSimuladoPrueba.class);

        assertThat(nivelPersistido(creada.revision().getId())).isNull();
        assertThat(eventosDeRiesgo(creada.revision().getId())).isEmpty();
    }

    private ResultadoRiesgo clasificar(String monto, boolean sensible, String perfilCodigo) {
        return clasificador.clasificar(
            new TipoOperacion("PRUEBA", "Tipo de prueba", sensible, true),
            new PerfilRiesgo(perfilCodigo, "Perfil de prueba", true),
            new BigDecimal(monto)
        );
    }

    private Usuario crearUsuario(String perfilCodigo) {
        PerfilRiesgo perfil = perfilRiesgoRepositorio.findById(perfilCodigo).orElseThrow();
        Rol rol = rolRepositorio.findById("SOLICITANTE").orElseThrow();
        return usuarioRepositorio.saveAndFlush(new Usuario(
            UUID.randomUUID(),
            "usuario-" + UUID.randomUUID(),
            "hash-de-prueba",
            "Usuario de prueba",
            perfil,
            Set.of(rol)
        ));
    }

    private SolicitudCreada crearSolicitud(Usuario usuario, String tipoCodigo, String monto) {
        TipoOperacion tipo = tipoOperacionRepositorio.findById(tipoCodigo).orElseThrow();
        Moneda moneda = monedaRepositorio.findById("PYG").orElseThrow();

        SolicitudFinanciera solicitud = solicitudRepositorio.saveAndFlush(
            new SolicitudFinanciera(usuario)
        );
        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                1,
                tipo,
                new BigDecimal(monto),
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
                new BigDecimal("1000000"),
                moneda,
                "Pago de demostración",
                "Operación autorizada para pruebas"
            )
        );
        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);
        return revision;
    }

    private void transicionar(SolicitudCreada creada, EstadoRevision destino) {
        servicioTransicion.transicionar(
            creada.solicitud().getId(),
            creada.revision().getId(),
            destino,
            new ContextoTransicion(null, null, "corr-" + destino, "Detalle de prueba")
        );
    }

    private String nivelPersistido(UUID revisionId) {
        return jdbcTemplate.queryForObject(
            "SELECT revisoli_nivel_riesgo FROM revision_solicitud WHERE revisoli_id = ?",
            String.class,
            revisionId
        );
    }

    private List<EventoSolicitud> eventosDeRiesgo(UUID revisionId) {
        return eventoRepositorio.buscarPorRevision(revisionId).stream()
            .filter(evento -> evento.getTipo() == TipoEventoSolicitud.RIESGO_CLASIFICADO)
            .toList();
    }

    private record CasoBorde(
        String monto,
        NivelRiesgo nivelEsperado,
        boolean requiereExterna
    ) {
    }

    private record SolicitudCreada(
        Usuario usuario,
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision
    ) {
    }
}
