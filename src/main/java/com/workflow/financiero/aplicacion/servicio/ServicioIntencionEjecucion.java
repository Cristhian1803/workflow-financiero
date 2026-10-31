package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoIntentoEjecucion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ResultadoAutorizacion;
import com.workflow.financiero.infraestructura.persistencia.IntentoEjecucionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
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

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final IntentoEjecucionRepositorio intentoRepositorio;
    private final VerificadorAutorizacionEjecucion verificador;
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioEventoSolicitud servicioEvento;
    private final PuertoProceso puertoProceso;

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

        RevisionSolicitud revision = intento.getRevision();
        if (revision.getIdentificadorProceso() == null
            || revision.getIdentificadorProceso().isBlank()) {
            throw new IllegalStateException(
                "La revisión no tiene un proceso asociado");
        }

        return Optional.of(new TrabajoEjecucion(
            intento.getId(),
            revision.getSolicitud().getId(),
            revision.getId(),
            revision.getIdentificadorProceso(),
            intento.getClave(),
            intento.getHuella(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo()
        ));
    }

    /**
     * Registra la confirmación y avanza el proceso en la misma transacción
     * corta. Si el proceso ya no espera el mensaje, la transacción se revierte
     * y la intención queda ENVIANDO para la recuperación posterior.
     */
    @Transactional
    public void registrarConfirmacion(
        TrabajoEjecucion trabajo,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion
    ) {
        IntentoEjecucion intento = intentoRepositorio
            .buscarPorIdParaActualizar(trabajo.intentoId())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la intención " + trabajo.intentoId()));

        if (intento.getEstado() == EstadoIntentoEjecucion.CONFIRMADA) {
            return;
        }
        if (intento.getEstado() != EstadoIntentoEjecucion.ENVIANDO) {
            throw new TransicionEstadoInvalidaException(
                "La intención no está en envío: " + intento.getEstado());
        }

        validarResultado(trabajo, reserva, confirmacion);

        RevisionSolicitud revision = intento.getRevision();
        SolicitudFinanciera solicitud = revision.getSolicitud();
        if (!revision.getId().equals(trabajo.revisionId())
            || !solicitud.getId().equals(trabajo.solicitudId())) {
            throw new TransicionEstadoInvalidaException(
                "La intención no corresponde al trabajo indicado");
        }
        if (revision.getEstado() != EstadoRevision.EJECUTANDO) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no está en ejecución: " + revision.getEstado());
        }

        intento.confirmar(
            reserva.referencia(),
            confirmacion.referencia(),
            confirmacion.id().toString()
        );
        intentoRepositorio.flush();

        puertoProceso.enviarMensaje(
            MENSAJE_EJECUCION_FINALIZADA,
            revision.getIdentificadorProceso(),
            Map.of(
                "resultadoEjecucion", "CONFIRMADA",
                "referenciaConfirmacion", confirmacion.referencia()
            )
        );
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
