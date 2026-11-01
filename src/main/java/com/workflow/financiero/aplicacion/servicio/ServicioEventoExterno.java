package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesRiesgoExterno;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.estado.EstadoEventoExterno;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.EventoExternoEnConflictoException;
import com.workflow.financiero.dominio.excepcion.EventoExternoInvalidoException;
import com.workflow.financiero.dominio.modelo.EvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.EventoExterno;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.riesgo.ConsolidadorRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
import com.workflow.financiero.dominio.riesgo.ClavesRiesgoExterno;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.EvaluacionRiesgoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.EventoExternoRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import com.workflow.financiero.infraestructura.seguridad.FirmaRiesgoExterno;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recibe, guarda y correlaciona eventos del proveedor de riesgo. El evento se
 * persiste antes de aplicar nada; si el proceso todavía no espera, queda
 * pendiente para el trabajador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioEventoExterno {

    public static final String MENSAJE_RIESGO = "RIESGO_EVALUADO";

    private static final List<EstadoEvaluacionRiesgo> ESTADOS_VIGENTES =
        List.of(
            EstadoEvaluacionRiesgo.PENDIENTE,
            EstadoEvaluacionRiesgo.RECIBIDA
        );

    private final EventoExternoRepositorio eventoRepositorio;
    private final EvaluacionRiesgoRepositorio evaluacionRepositorio;
    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioIncidente servicioIncidente;
    private final PuertoProceso puertoProceso;
    private final PropiedadesRiesgoExterno propiedades;
    private final Clock reloj;

    private final ConsolidadorRiesgo consolidador = new ConsolidadorRiesgo();

    @Transactional(noRollbackFor = EventoExternoEnConflictoException.class)
    public RegistroEventoExterno registrar(
        NotificacionRiesgoComando comando,
        byte[] cuerpo,
        String correlacion
    ) {
        validarFrescura(comando.fechaProveedor());
        NivelRiesgo nivel = nivel(comando.nivelRiesgo());
        String huella = FirmaRiesgoExterno.huella(cuerpo);

        SolicitudFinanciera solicitud = solicitudRepositorio
            .findById(comando.identificadorSolicitud())
            .orElse(null);
        RevisionSolicitud revision = revisionRepositorio
            .buscarPorSolicitudYNumero(
                comando.identificadorSolicitud(), comando.numeroRevision())
            .orElse(null);

        UUID id = UUID.randomUUID();
        Instant recibido = Instantes.ahora();
        int insertados = eventoRepositorio.insertarSiNoExiste(
            id,
            comando.proveedor(),
            comando.identificadorEvento(),
            comando.identificadorSolicitud(),
            comando.numeroRevision(),
            solicitud == null ? null : solicitud.getId(),
            revision == null ? null : revision.getId(),
            huella,
            nivel.name(),
            comando.resultado(),
            offset(comando.fechaProveedor()),
            correlacion,
            offset(recibido),
            offset(recibido)
        );

        EventoExterno evento = eventoRepositorio
            .buscarPorClaveParaActualizar(
                comando.proveedor(), comando.identificadorEvento())
            .orElseThrow(() -> new IllegalStateException(
                "No se pudo leer el evento externo recién registrado"));

        if (insertados == 0) {
            if (evento.getHuella().equals(huella)) {
                return new RegistroEventoExterno(
                    evento.getId(),
                    evento.getIdentificadorEvento(),
                    false,
                    evento.getRecibidoEn()
                );
            }

            evento.registrarConflicto(
                "Mismo identificador de evento con huella distinta");
            eventoRepositorio.flush();
            registrarConflictoAuditoria(evento, comando, correlacion);
            throw new EventoExternoEnConflictoException(
                "El identificador de evento ya existe con otro contenido");
        }

        return new RegistroEventoExterno(
            evento.getId(),
            evento.getIdentificadorEvento(),
            true,
            evento.getRecibidoEn()
        );
    }

    @Transactional
    public EstadoEventoExterno correlacionar(UUID idEvento) {
        EventoExterno evento = eventoRepositorio
            .buscarPorIdParaActualizar(idEvento)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe el evento externo " + idEvento));
        if (!evento.estaPendiente()) {
            return evento.getEstado();
        }

        UUID solicitudId = evento.getIdentificadorSolicitud();
        Integer numeroRevision = evento.getNumeroRevision();
        if (solicitudId == null || numeroRevision == null) {
            return obsoleto(
                evento,
                "El evento no tiene solicitud y revisión identificables");
        }

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElse(null);
        if (solicitud == null) {
            return esperar(evento, "La solicitud todavía no existe");
        }

        RevisionSolicitud revision = revisionRepositorio
            .buscarPorSolicitudYNumeroParaActualizar(
                solicitudId, numeroRevision)
            .orElse(null);
        if (revision == null) {
            evento.resolverSolicitud(solicitud, null);
            return esperar(evento, "La revisión todavía no existe");
        }
        evento.resolverSolicitud(solicitud, revision);

        RevisionSolicitud actual = solicitud.getRevisionActual();
        if (actual == null || !actual.getId().equals(revision.getId())) {
            return obsoleto(
                evento,
                "La revisión del evento ya no es la actual"
            );
        }
        if (evaluacionRepositorio.buscarVigentePorRevision(
                revision.getId(),
                OrigenRiesgo.MANUAL,
                List.of(EstadoEvaluacionRiesgo.MANUAL))
            .isPresent()) {
            return obsoleto(
                evento,
                "La revisión ya fue resuelta manualmente"
            );
        }

        EstadoRevision estado = revision.getEstado();
        if (estado == EstadoRevision.RECIBIDA
            || estado == EstadoRevision.VALIDANDO
            || estado == EstadoRevision.EVALUANDO_RIESGO) {
            return esperar(
                evento,
                "La revisión todavía no está esperando el riesgo externo"
            );
        }
        if (estado != EstadoRevision.PENDIENTE_APROBACION) {
            return obsoleto(
                evento,
                "La revisión no admite el resultado externo: " + estado
            );
        }
        if (revision.getIdentificadorProceso() == null
            || !puertoProceso.esperaMensaje(
                MENSAJE_RIESGO, revision.getIdentificadorProceso())) {
            return esperar(
                evento,
                "El proceso todavía no espera el riesgo externo"
            );
        }

        EvaluacionRiesgo evaluacion = evaluacionRepositorio
            .buscarVigentePorRevisionParaActualizar(
                revision.getId(), OrigenRiesgo.EXTERNO, ESTADOS_VIGENTES)
            .orElse(null);
        if (evaluacion == null) {
            evaluacion = new EvaluacionRiesgo(
                revision,
                OrigenRiesgo.EXTERNO,
                EstadoEvaluacionRiesgo.PENDIENTE,
                ClavesRiesgoExterno.solicitud(
                    solicitudId, revision.getId()),
                evento.getCorrelacion(),
                evento.getProveedor()
            );
            evaluacionRepositorio.saveAndFlush(evaluacion);
        }
        if (evaluacion.getEstado() == EstadoEvaluacionRiesgo.RECIBIDA) {
            return obsoleto(
                evento,
                "La evaluación externa ya fue aplicada"
            );
        }

        NivelRiesgo externo = evento.getNivelRiesgo();
        if (externo == null) {
            return esperar(
                evento,
                "El evento no tiene un nivel de riesgo válido"
            );
        }

        ResultadoRiesgo consolidado = consolidador.consolidar(
            interno(revision), externo);
        Instant ahora = Instantes.ahora();

        evaluacion.marcarRecibida(
            externo,
            evento.getResultado(),
            evento.getIdentificadorEvento(),
            evento.getProveedor(),
            ahora
        );
        revision.consolidarNivelRiesgo(consolidado.nivel());

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EVALUACION_EXTERNA_RESUELTA,
            null,
            null,
            revision.getEstado(),
            revision.getEstado(),
            evento.getCorrelacion(),
            "Riesgo externo " + externo
                + "; riesgo consolidado " + consolidado.nivel()
                + "; evento " + evento.getIdentificadorEvento()
        );

        evento.marcarProcesado(ahora);
        evaluacionRepositorio.flush();
        revisionRepositorio.flush();
        eventoRepositorio.flush();
        servicioIncidente.resolverRiesgoExternoPorRevision(
            revision.getId(),
            null,
            "Evento externo procesado",
            evento.getCorrelacion()
        );

        puertoProceso.enviarMensaje(
            MENSAJE_RIESGO,
            revision.getIdentificadorProceso(),
            Map.of(
                "riesgoExternoResuelto", true,
                "nivelRiesgoExterno", consolidado.nivel().name(),
                "identificadorEventoExterno", evento.getIdentificadorEvento()
            )
        );
        return EstadoEventoExterno.PROCESADO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalloCorrelacion(UUID idEvento, String error) {
        EventoExterno evento = eventoRepositorio
            .buscarPorIdParaActualizar(idEvento)
            .orElse(null);
        if (evento == null || !evento.estaPendiente()) {
            return;
        }
        evento.programarReintento(
            error,
            Instantes.ahora().plus(propiedades.reintento()),
            propiedades.maximoIntentos()
        );
        eventoRepositorio.flush();
        if (evento.getProximoIntentoEn() == null
            && evento.getSolicitud() != null
            && evento.getRevision() != null) {
            servicioIncidente.crearRiesgoExterno(
                evento.getSolicitud(),
                evento.getRevision(),
                "Se agotaron los reintentos de correlación del evento externo",
                evento.getCorrelacion()
            );
        }
    }

    @Transactional
    public Optional<UUID> reclamarSiguienteParaCorrelacion() {
        OffsetDateTime ahora = OffsetDateTime.ofInstant(
            Instantes.ahora(), ZoneOffset.UTC);
        return eventoRepositorio
            .reclamarSiguiente(ahora, propiedades.maximoIntentos())
            .map(EventoExterno::getId);
    }

    private EstadoEventoExterno esperar(
        EventoExterno evento,
        String detalle
    ) {
        evento.programarEspera(
            detalle,
            Instantes.ahora().plus(propiedades.reintento())
        );
        eventoRepositorio.flush();
        return EstadoEventoExterno.PENDIENTE;
    }

    private EstadoEventoExterno obsoleto(
        EventoExterno evento,
        String detalle
    ) {
        Instant ahora = Instantes.ahora();
        evento.marcarObsoleto(detalle, ahora);

        if (evento.getRevision() != null) {
            evaluacionRepositorio
                .buscarVigentePorRevisionParaActualizar(
                    evento.getRevision().getId(),
                    OrigenRiesgo.EXTERNO,
                    ESTADOS_VIGENTES)
                .ifPresent(evaluacion -> {
                    if (evaluacion.estaPendiente()) {
                        evaluacion.marcarObsoleta(detalle);
                    }
                });
        }

        eventoRepositorio.flush();
        return EstadoEventoExterno.OBSOLETO;
    }

    private ResultadoRiesgo interno(RevisionSolicitud revision) {
        return new ResultadoRiesgo(
            revision.getNivelRiesgo(),
            OrigenRiesgo.INTERNO,
            true,
            false,
            "Riesgo interno persistido"
        );
    }

    private void registrarConflictoAuditoria(
        EventoExterno evento,
        NotificacionRiesgoComando comando,
        String correlacion
    ) {
        if (evento.getSolicitud() == null || evento.getRevision() == null) {
            log.warn(
                "Conflicto de evento externo sin revisión correlacionada: {}",
                comando.identificadorEvento()
            );
            return;
        }
        servicioEvento.registrar(
            evento.getSolicitud(),
            evento.getRevision(),
            TipoEventoSolicitud.EVALUACION_EXTERNA_CONFLICTO,
            null,
            null,
            evento.getRevision().getEstado(),
            evento.getRevision().getEstado(),
            correlacion,
            "Conflicto del evento externo "
                + comando.identificadorEvento()
        );
    }

    private void validarFrescura(Instant fechaProveedor) {
        if (fechaProveedor == null) {
            throw new EventoExternoInvalidoException(
                "La fecha del proveedor es obligatoria");
        }
        Duration diferencia = Duration.between(
            fechaProveedor, reloj.instant()).abs();
        if (diferencia.compareTo(propiedades.ventanaTiempo()) > 0) {
            throw new EventoExternoInvalidoException(
                "La fecha del proveedor está fuera de la ventana permitida");
        }
    }

    private NivelRiesgo nivel(String valor) {
        try {
            return NivelRiesgo.valueOf(
                valor.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException excepcion) {
            throw new EventoExternoInvalidoException(
                "El nivel de riesgo externo no es válido: " + valor);
        }
    }

    private OffsetDateTime offset(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
