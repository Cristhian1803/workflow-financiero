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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        jdbcTemplate.execute(
            "TRUNCATE TABLE solicitud_financiera, usuario, evento_externo CASCADE"
        );
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

    @Test
    @DisplayName("No permite dos tareas para la misma revisión y rol")
    void noPermiteDosTareasParaLaMismaRevisionYRol() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID revisionId = creada.revision().getId();
        UUID usuarioId = creada.usuario().getId();

        insertarTarea(revisionId, "SUPERVISOR", UUID.randomUUID().toString(), usuarioId);

        assertThatThrownBy(() -> insertarTarea(
            revisionId, "SUPERVISOR", UUID.randomUUID().toString(), usuarioId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No permite reutilizar el identificador de tarea del motor")
    void noPermiteReutilizarIdentificadorDeTareaDelMotor() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID revisionId = creada.revision().getId();
        UUID usuarioId = creada.usuario().getId();
        String identificador = "motor-" + UUID.randomUUID();

        insertarTarea(revisionId, "SUPERVISOR", identificador, usuarioId);

        assertThatThrownBy(() -> insertarTarea(
            revisionId, "GERENTE", identificador, usuarioId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Permite varias tareas sin identificador de motor")
    void permiteVariasTareasSinIdentificadorDeMotor() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID revisionId = creada.revision().getId();
        UUID usuarioId = creada.usuario().getId();

        insertarTarea(revisionId, "SUPERVISOR", null, usuarioId);
        insertarTarea(revisionId, "GERENTE", null, usuarioId);

        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tarea_aprobacion WHERE tareapro_revisoli_id = ?",
            Long.class,
            revisionId
        );
        assertThat(total).isEqualTo(2L);
    }

    @Test
    @DisplayName("No permite dos decisiones para la misma tarea")
    void noPermiteDosDecisionesParaLaMismaTarea() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID tareaId = insertarTarea(
            creada.revision().getId(),
            "SUPERVISOR",
            UUID.randomUUID().toString(),
            creada.usuario().getId()
        );

        insertarDecision(tareaId, creada.usuario().getId(), "APROBADA");

        assertThatThrownBy(() -> insertarDecision(
            tareaId, creada.usuario().getId(), "RECHAZADA"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No permite eventos externos duplicados por proveedor e identificador")
    void noPermiteEventosExternosDuplicados() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));

        insertarEventoExterno(
            "proveedor-a",
            "evento-1",
            creada.solicitud().getId(),
            creada.revision().getId()
        );

        assertThatThrownBy(() -> insertarEventoExterno(
            "proveedor-a",
            "evento-1",
            creada.solicitud().getId(),
            creada.revision().getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La revisión del evento externo debe pertenecer a la solicitud")
    void laRevisionDelEventoExternoDebePertenecerALaSolicitud() {
        SolicitudCreada primera = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        SolicitudCreada segunda = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));

        assertThatThrownBy(() -> insertarEventoExterno(
            "proveedor-a",
            "evento-1",
            primera.solicitud().getId(),
            segunda.revision().getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Permite eventos externos pendientes de correlación")
    void permiteEventosExternosPendientesDeCorrelacion() {
        insertarEventoExterno("proveedor-a", "evento-1", null, null);

        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evento_externo "
                + "WHERE evenexte_solifina_id IS NULL AND evenexte_revisoli_id IS NULL",
            Long.class
        );
        assertThat(total).isEqualTo(1L);
    }

    @Test
    @DisplayName("No permite notificaciones duplicadas por destinatario")
    void noPermiteNotificacionesDuplicadasPorDestinatario() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID tareaId = insertarTarea(
            creada.revision().getId(),
            "SUPERVISOR",
            UUID.randomUUID().toString(),
            creada.usuario().getId()
        );

        insertarNotificacion(
            tareaId, creada.usuario().getId(), null, "VENCIMIENTO", "PENDIENTE"
        );
        assertThatThrownBy(() -> insertarNotificacion(
            tareaId, creada.usuario().getId(), null, "VENCIMIENTO", "PENDIENTE"
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertarNotificacion(tareaId, null, "SUPERVISOR", "VENCIMIENTO", "PENDIENTE");
        assertThatThrownBy(() -> insertarNotificacion(
            tareaId, null, "SUPERVISOR", "VENCIMIENTO", "PENDIENTE"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No permite notificaciones sin destinatario")
    void noPermiteNotificacionesSinDestinatario() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID tareaId = insertarTarea(
            creada.revision().getId(),
            "SUPERVISOR",
            UUID.randomUUID().toString(),
            creada.usuario().getId()
        );

        assertThatThrownBy(() -> insertarNotificacion(
            tareaId, null, null, "VENCIMIENTO", "PENDIENTE"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No permite idempotencia duplicada por usuario, operación y clave")
    void noPermiteIdempotenciaDuplicada() {
        Usuario usuario = crearUsuario("usuario-" + UUID.randomUUID());

        insertarIdempotencia(usuario.getId(), "crear-solicitud", "clave-1");

        assertThatThrownBy(() -> insertarIdempotencia(
            usuario.getId(), "crear-solicitud", "clave-1"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("No permite dos intenciones para la misma revisión ni la misma clave")
    void noPermiteIntentosDuplicados() {
        SolicitudCreada primera = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        insertarIntento(primera.revision().getId(), "clave-a");

        assertThatThrownBy(() -> insertarIntento(primera.revision().getId(), "clave-b"))
            .isInstanceOf(DataIntegrityViolationException.class);

        SolicitudCreada segunda = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        assertThatThrownBy(() -> insertarIntento(segunda.revision().getId(), "clave-a"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Permite varias evaluaciones de riesgo para la misma revisión")
    void permiteVariasEvaluacionesParaLaMismaRevision() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));

        insertarEvaluacion(creada.revision().getId(), "INTERNO", "BAJO");
        insertarEvaluacion(creada.revision().getId(), "EXTERNO", "MEDIO");

        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evaluacion_riesgo WHERE evalries_revisoli_id = ?",
            Long.class,
            creada.revision().getId()
        );
        assertThat(total).isEqualTo(2L);
    }

    @Test
    @DisplayName("Los checks rechazan estados, decisiones y niveles inválidos")
    void checksRechazanValoresInvalidos() {
        SolicitudCreada creada = crearSolicitud(crearUsuario("usuario-" + UUID.randomUUID()));
        UUID revisionId = creada.revision().getId();
        UUID usuarioId = creada.usuario().getId();

        assertThatThrownBy(() -> insertarTarea(
            revisionId, "SUPERVISOR", null, usuarioId, "INVALIDA"
        )).isInstanceOf(DataIntegrityViolationException.class);

        UUID tareaId = insertarTarea(revisionId, "GERENTE", null, usuarioId);
        assertThatThrownBy(() -> insertarDecision(tareaId, usuarioId, "OTRA"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertarEvaluacion(revisionId, "OTRO", "BAJO"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertarNotificacion(
            tareaId, usuarioId, null, "VENCIMIENTO", "OTRA"
        )).isInstanceOf(DataIntegrityViolationException.class);
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

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private UUID insertarTarea(
        UUID revisionId,
        String rol,
        String identificadorMotor,
        UUID asignadoId
    ) {
        return insertarTarea(revisionId, rol, identificadorMotor, asignadoId, "PENDIENTE");
    }

    private UUID insertarTarea(
        UUID revisionId,
        String rol,
        String identificadorMotor,
        UUID asignadoId,
        String estado
    ) {
        UUID id = UUID.randomUUID();
        OffsetDateTime instante = ahora();
        jdbcTemplate.update(
            "INSERT INTO tarea_aprobacion ("
                + "tareapro_id, tareapro_revisoli_id, tareapro_rol_codigo, "
                + "tareapro_identificador_tarea_motor, tareapro_asignado_usua_id, "
                + "tareapro_estado, tareapro_fecha_vencimiento, tareapro_version, "
                + "tareapro_creado_en) "
                + "VALUES (?, ?, ?, CAST(? AS VARCHAR), CAST(? AS UUID), ?, ?, 0, ?)",
            id,
            revisionId,
            rol,
            identificadorMotor,
            asignadoId,
            estado,
            instante.plusHours(24),
            instante
        );
        return id;
    }

    private UUID insertarDecision(UUID tareaId, UUID actorId, String decision) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO decision_aprobacion ("
                + "deciapro_id, deciapro_tareapro_id, deciapro_actor_usua_id, "
                + "deciapro_decision, deciapro_motivo, deciapro_fecha) "
                + "VALUES (?, ?, ?, ?, CAST(? AS VARCHAR), ?)",
            id,
            tareaId,
            actorId,
            decision,
            null,
            ahora()
        );
        return id;
    }

    private UUID insertarEventoExterno(
        String proveedor,
        String identificadorEvento,
        UUID solicitudId,
        UUID revisionId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO evento_externo ("
                + "evenexte_id, evenexte_proveedor, evenexte_identificador_evento, "
                + "evenexte_solifina_id, evenexte_revisoli_id, evenexte_huella, "
                + "evenexte_estado, evenexte_recibido_en) "
                + "VALUES (?, ?, ?, CAST(? AS UUID), CAST(? AS UUID), ?, 'RECIBIDO', ?)",
            id,
            proveedor,
            identificadorEvento,
            solicitudId,
            revisionId,
            "huella-" + id,
            ahora()
        );
        return id;
    }

    private UUID insertarNotificacion(
        UUID tareaId,
        UUID destinatarioUsuarioId,
        String destinatarioRol,
        String tipo,
        String estado
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO notificacion ("
                + "noti_id, noti_tareapro_id, noti_destinatario_usua_id, "
                + "noti_destinatario_rol_codigo, noti_tipo, noti_contenido, "
                + "noti_estado, noti_creado_en, noti_leida_en) "
                + "VALUES (?, CAST(? AS UUID), CAST(? AS UUID), CAST(? AS VARCHAR), "
                + "?, ?, ?, ?, CAST(? AS TIMESTAMP WITH TIME ZONE))",
            id,
            tareaId,
            destinatarioUsuarioId,
            destinatarioRol,
            tipo,
            "Contenido de prueba",
            estado,
            ahora(),
            null
        );
        return id;
    }

    private UUID insertarIdempotencia(UUID usuarioId, String operacion, String clave) {
        UUID id = UUID.randomUUID();
        OffsetDateTime instante = ahora();
        jdbcTemplate.update(
            "INSERT INTO registro_idempotencia ("
                + "regiidem_id, regiidem_usua_id, regiidem_operacion, regiidem_clave, "
                + "regiidem_huella, regiidem_codigo_respuesta, regiidem_cuerpo_respuesta, "
                + "regiidem_expira_en, regiidem_creado_en) "
                + "VALUES (?, ?, ?, ?, ?, CAST(? AS INTEGER), CAST(? AS VARCHAR), ?, ?)",
            id,
            usuarioId,
            operacion,
            clave,
            "huella-" + id,
            null,
            null,
            instante.plusHours(24),
            instante
        );
        return id;
    }

    private UUID insertarIntento(UUID revisionId, String clave) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO intento_ejecucion ("
                + "inteejec_id, inteejec_revisoli_id, inteejec_clave, inteejec_huella, "
                + "inteejec_estado, inteejec_referencia_externa, inteejec_version, "
                + "inteejec_creado_en, inteejec_actualizado_en) "
                + "VALUES (?, ?, ?, ?, 'PENDIENTE_ENVIO', CAST(? AS VARCHAR), 0, ?, "
                + "CAST(? AS TIMESTAMP WITH TIME ZONE))",
            id,
            revisionId,
            clave,
            "huella-" + id,
            null,
            ahora(),
            null
        );
        return id;
    }

    private UUID insertarEvaluacion(UUID revisionId, String origen, String nivel) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO evaluacion_riesgo ("
                + "evalries_id, evalries_revisoli_id, evalries_origen, evalries_nivel, "
                + "evalries_resultado, evalries_correlacion, evalries_creado_en) "
                + "VALUES (?, ?, ?, ?, CAST(? AS VARCHAR), CAST(? AS VARCHAR), ?)",
            id,
            revisionId,
            origen,
            nivel,
            null,
            null,
            ahora()
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
