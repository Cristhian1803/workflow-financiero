package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesEjecucion;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoIntentoEjecucion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.EjecucionNoPermitidaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ResultadoAutorizacion;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.IntentoEjecucionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepara, reclama y confirma la intención financiera durable de una revisión.
 * La llamada HTTP nunca ocurre dentro de estos métodos.
 */
@Service
@RequiredArgsConstructor
public class ServicioIntencionEjecucion {

    public static final String MENSAJE_EJECUCION_FINALIZADA =
        "EJECUCION_FINALIZADA";

    private static final String CORRELACION_RECUPERACION =
        "recuperacion-ejecucion";

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final IntentoEjecucionRepositorio intentoRepositorio;
    private final VerificadorAutorizacionEjecucion verificador;
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioIncidente servicioIncidente;
    private final PuertoProceso puertoProceso;
    private final PropiedadesEjecucion propiedades;

    /**
     * Persiste la intención y deja la revisión en EJECUTANDO. Es idempotente:
     * si ya existe una intención compatible, la devuelve sin repetir eventos.
     */
    @Transactional
    public IntentoEjecucion preparar(
        UUID solicitudId,
        UUID revisionId,
        String correlacion
    ) {
        if (solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias");
        }

        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));

        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));

        if (!revision.getSolicitud().getId().equals(solicitudId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no pertenece a la solicitud indicada");
        }
        RevisionSolicitud actual = solicitud.getRevisionActual();
        if (actual == null || !actual.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }

        String clave = claveFinanciera(solicitudId, revision);
        String huella = huellaFinanciera(solicitudId, revision);

        Optional<IntentoEjecucion> existente =
            intentoRepositorio.buscarPorRevisionId(revisionId);
        if (existente.isPresent()) {
            IntentoEjecucion intento = existente.get();
            if (!intento.getClave().equals(clave)
                || !intento.getHuella().equals(huella)) {
                throw new TransicionEstadoInvalidaException(
                    "Ya existe una intención incompatible para la revisión");
            }
            return intento;
        }

        if (revision.getEstado() != EstadoRevision.APROBADA) {
            throw new TransicionEstadoInvalidaException(
                "No se puede preparar la ejecución en estado "
                    + revision.getEstado());
        }

        ResultadoAutorizacion autorizacion = verificador.verificar(
            solicitudId, revisionId);
        if (!autorizacion.autorizado()) {
            throw new TransicionEstadoInvalidaException(
                "No se autoriza la ejecución: "
                    + String.join("; ", autorizacion.motivos()));
        }

        IntentoEjecucion intento = new IntentoEjecucion(
            revision, clave, huella);
        intentoRepositorio.saveAndFlush(intento);

        servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.EJECUTANDO,
            new ContextoTransicion(
                null,
                null,
                correlacion,
                "Ejecución iniciada"
            )
        );

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EJECUCION_INICIADA,
            null,
            null,
            EstadoRevision.APROBADA,
            EstadoRevision.EJECUTANDO,
            correlacion,
            "Intención de ejecución preparada"
        );

        return intento;
    }

    /**
     * Reclama la intención pendiente más antigua y devuelve los datos que el
     * trabajador necesita para llamar al proveedor fuera de transacción.
     */
    @Transactional
    public Optional<TrabajoEjecucion> reclamarSiguiente(String trabajador) {
        Optional<IntentoEjecucion> reclamada =
            intentoRepositorio.reclamarPendiente();
        if (reclamada.isEmpty()) {
            return Optional.empty();
        }

        IntentoEjecucion intento = reclamada.get();
        intento.reclamar(trabajador);
        intentoRepositorio.flush();
        return Optional.of(trabajo(intento));
    }

    /**
     * Reclama una intención que quedó en envío o que necesita una nueva
     * consulta. La consulta externa se hace después de cerrar esta transacción.
     */
    @Transactional
    public Optional<TrabajoEjecucion> reclamarSiguienteRecuperacion(
        String trabajador
    ) {
        OffsetDateTime ahora = OffsetDateTime.ofInstant(
            Instantes.ahora(), ZoneOffset.UTC);
        OffsetDateTime limite = ahora.minus(
            propiedades.reclamacionVencida());

        Optional<IntentoEjecucion> reclamada =
            intentoRepositorio.reclamarRecuperable(
                limite, ahora, propiedades.maxIntentos());
        if (reclamada.isEmpty()) {
            return Optional.empty();
        }

        IntentoEjecucion intento = reclamada.get();
        intento.reclamarRecuperacion(trabajador);
        intentoRepositorio.flush();
        return Optional.of(trabajo(intento));
    }

    /**
     * Registra la confirmación y avanza el proceso en la misma transacción
     * corta. Si el proceso ya no espera el mensaje, la transición se aplica
     * directamente para que la recuperación no dependa de una instancia viva.
     */
    @Transactional
    public void registrarConfirmacion(
        TrabajoEjecucion trabajo,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion
    ) {
        registrarConfirmacion(
            trabajo,
            reserva,
            confirmacion,
            "Ejecución confirmada por el proveedor"
        );
    }

    @Transactional
    public void registrarConfirmacion(
        TrabajoEjecucion trabajo,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion,
        String detalle
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(trabajo.solicitudId());
        RevisionSolicitud revision = bloquearRevision(trabajo.revisionId());
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        validarCorrespondencia(solicitud, revision, trabajo);

        if (intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA
            || revision.getEstado() == EstadoRevision.EJECUTADA) {
            return;
        }
        if (revision.getEstado() == EstadoRevision.RECHAZADA
            || revision.getEstado() == EstadoRevision.CANCELADA) {
            throw new EjecucionNoPermitidaException(
                "La revisión ya está en estado " + revision.getEstado());
        }

        validarResultado(trabajo, reserva, confirmacion);

        intento.confirmar(
            reserva.referencia(),
            confirmacion.referencia(),
            confirmacion.id().toString()
        );
        intentoRepositorio.flush();

        if (puertoProceso.esperaMensaje(
                MENSAJE_EJECUCION_FINALIZADA, trabajo.procesoId())) {
            puertoProceso.enviarMensaje(
                MENSAJE_EJECUCION_FINALIZADA,
                trabajo.procesoId(),
                Map.of(
                    "resultadoEjecucion", "CONFIRMADA",
                    "referenciaConfirmacion", confirmacion.referencia()
                )
            );
        } else {
            EstadoRevision anterior = revision.getEstado();
            servicioTransicion.transicionar(
                trabajo.solicitudId(),
                trabajo.revisionId(),
                EstadoRevision.EJECUTADA,
                new ContextoTransicion(
                    null,
                    null,
                    CORRELACION_RECUPERACION,
                    detalle
                )
            );
            servicioEvento.registrar(
                solicitud,
                revision,
                TipoEventoSolicitud.EJECUCION_CONFIRMADA,
                null,
                null,
                anterior,
                EstadoRevision.EJECUTADA,
                CORRELACION_RECUPERACION,
                detalle
            );
            servicioIncidente.resolverPorRevision(
                trabajo.revisionId(),
                null,
                "Ejecución confirmada por consulta",
                CORRELACION_RECUPERACION
            );
        }
    }

    /**
     * Persiste el resultado desconocido y crea el incidente. No cambia todavía
     * la revisión: primero se intenta reconciliar con el proveedor.
     */
    @Transactional
    public UUID registrarResultadoDesconocido(
        TrabajoEjecucion trabajo,
        String motivo
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(trabajo.solicitudId());
        RevisionSolicitud revision = bloquearRevision(trabajo.revisionId());
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        validarCorrespondencia(solicitud, revision, trabajo);

        if (intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA
            || revision.getEstado().esTerminal()) {
            return null;
        }

        intento.marcarResultadoDesconocido(motivo);
        intentoRepositorio.flush();

        return servicioIncidente.crearEjecucion(
            solicitud,
            revision,
            intento.getId(),
            motivo,
            true,
            CORRELACION_RECUPERACION
        ).getId();
    }

    /**
     * Registra un fallo definitivo sin efecto. El incidente queda abierto y
     * puede resolverse con un reintento manual seguro.
     */
    @Transactional
    public UUID registrarFalloDefinitivo(
        TrabajoEjecucion trabajo,
        String motivo,
        boolean reintentable
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(trabajo.solicitudId());
        RevisionSolicitud revision = bloquearRevision(trabajo.revisionId());
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        validarCorrespondencia(solicitud, revision, trabajo);

        if (intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA
            || revision.getEstado().esTerminal()) {
            return null;
        }

        EstadoIntentoEjecucion anterior = intento.getEstado();
        intento.marcarFallida(motivo);
        intentoRepositorio.flush();

        if (anterior != EstadoIntentoEjecucion.FALLIDA) {
            servicioEvento.registrar(
                solicitud,
                revision,
                TipoEventoSolicitud.EJECUCION_FALLIDA,
                null,
                null,
                revision.getEstado(),
                revision.getEstado(),
                CORRELACION_RECUPERACION,
                motivo
            );
        }

        return servicioIncidente.crearEjecucion(
            solicitud,
            revision,
            intento.getId(),
            motivo,
            reintentable,
            CORRELACION_RECUPERACION
        ).getId();
    }

    /**
     * Cierra la espera del proceso con resultado desconocido. Si el proceso ya
     * no existe, aplica la transición directamente.
     */
    @Transactional
    public void cerrarEjecucionDesconocida(
        TrabajoEjecucion trabajo,
        String motivo
    ) {
        SolicitudFinanciera solicitud = bloquearSolicitud(trabajo.solicitudId());
        RevisionSolicitud revision = bloquearRevision(trabajo.revisionId());
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        validarCorrespondencia(solicitud, revision, trabajo);

        if (revision.getEstado().esTerminal()
            || revision.getEstado() == EstadoRevision.RESULTADO_DESCONOCIDO
            || intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }

        if (puertoProceso.esperaMensaje(
                MENSAJE_EJECUCION_FINALIZADA, trabajo.procesoId())) {
            puertoProceso.enviarMensaje(
                MENSAJE_EJECUCION_FINALIZADA,
                trabajo.procesoId(),
                Map.of(
                    "resultadoEjecucion", "RESULTADO_DESCONOCIDO",
                    "motivo", motivo
                )
            );
            return;
        }

        EstadoRevision anterior = revision.getEstado();
        servicioTransicion.transicionar(
            trabajo.solicitudId(),
            trabajo.revisionId(),
            EstadoRevision.RESULTADO_DESCONOCIDO,
            new ContextoTransicion(
                null,
                null,
                CORRELACION_RECUPERACION,
                motivo
            )
        );
        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.EJECUCION_RESULTADO_DESCONOCIDO,
            null,
            null,
            anterior,
            EstadoRevision.RESULTADO_DESCONOCIDO,
            CORRELACION_RECUPERACION,
            motivo
        );
    }

    @Transactional
    public void programarReintento(TrabajoEjecucion trabajo, String motivo) {
        SolicitudFinanciera solicitud = bloquearSolicitud(trabajo.solicitudId());
        RevisionSolicitud revision = bloquearRevision(trabajo.revisionId());
        if (revision.getEstado().esTerminal()) {
            return;
        }
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        if (intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        intento.programarReintento(
            Instantes.ahora().plus(propiedades.reintentoEspera()),
            motivo
        );
        intentoRepositorio.flush();
    }

    @Transactional
    public void detenerReintentoAutomatico(
        TrabajoEjecucion trabajo,
        String motivo
    ) {
        IntentoEjecucion intento = bloquearIntento(trabajo.intentoId());
        intento.detenerReintentoAutomatico(motivo);
        intentoRepositorio.flush();
    }

    /**
     * Prepara un reintento manual autorizado. La clave y la huella no cambian.
     */
    @Transactional
    public TrabajoEjecucion reclamarParaReintentoManual(
        UUID intentoId,
        String trabajador
    ) {
        IntentoEjecucion referencia = intentoRepositorio.findById(intentoId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la intención " + intentoId));
        UUID solicitudId = referencia.getRevision().getSolicitud().getId();
        UUID revisionId = referencia.getRevision().getId();

        SolicitudFinanciera solicitud = bloquearSolicitud(solicitudId);
        RevisionSolicitud revision = bloquearRevision(revisionId);
        IntentoEjecucion intento = bloquearIntento(intentoId);
        validarCorrespondencia(
            solicitud,
            revision,
            new TrabajoEjecucion(
                intentoId,
                solicitudId,
                revisionId,
                revision.getIdentificadorProceso(),
                intento.getClave(),
                intento.getHuella(),
                revision.getMonto(),
                revision.getMoneda().getCodigo(),
                revision.getTipoOperacion().getCodigo(),
                intento.getEstado(),
                intento.getIntentos()
            )
        );

        if (revision.getEstado() == EstadoRevision.EJECUTADA
            || revision.getEstado() == EstadoRevision.RECHAZADA
            || revision.getEstado() == EstadoRevision.CANCELADA) {
            throw new EjecucionNoPermitidaException(
                "La revisión no admite reintento en estado "
                    + revision.getEstado());
        }

        ResultadoAutorizacion autorizacion = verificador.verificar(
            solicitudId, revisionId);
        if (!autorizacion.autorizado()) {
            throw new EjecucionNoPermitidaException(
                "No se autoriza el reintento: "
                    + String.join("; ", autorizacion.motivos()));
        }

        intento.reintentarManual(trabajador);
        intentoRepositorio.flush();
        return trabajo(intento);
    }

    private TrabajoEjecucion trabajo(IntentoEjecucion intento) {
        RevisionSolicitud revision = intento.getRevision();
        if (revision.getIdentificadorProceso() == null
            || revision.getIdentificadorProceso().isBlank()) {
            throw new IllegalStateException(
                "La revisión no tiene un proceso asociado");
        }
        return new TrabajoEjecucion(
            intento.getId(),
            revision.getSolicitud().getId(),
            revision.getId(),
            revision.getIdentificadorProceso(),
            intento.getClave(),
            intento.getHuella(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            intento.getEstado(),
            intento.getIntentos()
        );
    }

    private SolicitudFinanciera bloquearSolicitud(UUID solicitudId) {
        return solicitudRepositorio.buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));
    }

    private RevisionSolicitud bloquearRevision(UUID revisionId) {
        return revisionRepositorio.buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));
    }

    private IntentoEjecucion bloquearIntento(UUID intentoId) {
        return intentoRepositorio.buscarPorIdParaActualizar(intentoId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la intención " + intentoId));
    }

    private void validarCorrespondencia(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        TrabajoEjecucion trabajo
    ) {
        if (!revision.getId().equals(trabajo.revisionId())
            || !solicitud.getId().equals(trabajo.solicitudId())) {
            throw new TransicionEstadoInvalidaException(
                "La intención no corresponde al trabajo indicado");
        }
        RevisionSolicitud actual = solicitud.getRevisionActual();
        if (actual == null || !actual.getId().equals(revision.getId())) {
            throw new EjecucionNoPermitidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
    }

    private void validarResultado(
        TrabajoEjecucion trabajo,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion
    ) {
        if (reserva == null || confirmacion == null) {
            throw new IllegalArgumentException(
                "Los resultados del proveedor son obligatorios");
        }
        if (!reserva.clave().equals(
                "reserva:" + trabajo.solicitudId() + ":"
                    + trabajo.revisionId())
            || !confirmacion.clave().equals(
                "confirmacion:" + trabajo.solicitudId() + ":"
                    + trabajo.revisionId())) {
            throw new TransicionEstadoInvalidaException(
                "Las claves del proveedor no corresponden a la intención");
        }
        if (!confirmacion.huella().equals(trabajo.huella())) {
            throw new TransicionEstadoInvalidaException(
                "La huella confirmada no corresponde a la intención");
        }
        if (confirmacion.estado() != EstadoOperacionFinanciera.CONFIRMADA) {
            throw new TransicionEstadoInvalidaException(
                "El proveedor no devolvió una confirmación efectiva");
        }
    }

    private String claveFinanciera(
        UUID solicitudId,
        RevisionSolicitud revision
    ) {
        return solicitudId + ":" + revision.getNumero();
    }

    private String huellaFinanciera(
        UUID solicitudId,
        RevisionSolicitud revision
    ) {
        return HuellaOperacionFinanciera.calcular(
            AccionFinanciera.CONFIRMACION,
            solicitudId,
            revision.getId(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo()
        );
    }
}
