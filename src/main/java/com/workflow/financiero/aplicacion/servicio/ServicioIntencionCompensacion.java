package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesEjecucion;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.estado.EstadoIntentoCompensacion;
import com.workflow.financiero.dominio.modelo.IntentoCompensacion;
import com.workflow.financiero.dominio.modelo.IntentoEjecucion;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.IntentoCompensacionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.IntentoEjecucionRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transiciones transaccionales de la intención de compensación. Las llamadas
 * al proveedor ocurren fuera de estas transacciones.
 */
@Service
@RequiredArgsConstructor
public class ServicioIntencionCompensacion {

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final IntentoEjecucionRepositorio intentoEjecucionRepositorio;
    private final IntentoCompensacionRepositorio compensacionRepositorio;
    private final PropiedadesEjecucion propiedades;

    /**
     * Crea una sola intención de compensación por revisión. Si ya existe, la
     * devuelve sin cambiar su clave ni su huella.
     */
    @Transactional
    public IntentoCompensacion crearObtener(
        UUID solicitudId,
        UUID revisionId,
        Usuario actor,
        String motivo,
        boolean despachoEnCurso
    ) {
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la solicitud " + solicitudId));
        RevisionSolicitud revision = revisionRepositorio
            .buscarPorIdParaActualizar(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));
        if (!revision.getSolicitud().getId().equals(solicitud.getId())) {
            throw new IllegalArgumentException(
                "La revisión no pertenece a la solicitud indicada");
        }

        Optional<IntentoCompensacion> existente = compensacionRepositorio
            .buscarPorRevisionIdParaActualizar(revisionId);
        if (existente.isPresent()) {
            return existente.get();
        }

        String clave = ClavesOperacionFinanciera.liberacion(
            solicitudId, revisionId);
        String huella = HuellaOperacionFinanciera.calcular(
            AccionFinanciera.LIBERACION,
            solicitudId,
            revisionId,
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo()
        );
        IntentoCompensacion compensacion = new IntentoCompensacion(
            revision,
            clave,
            huella,
            despachoEnCurso,
            actor,
            motivo
        );
        return compensacionRepositorio.saveAndFlush(compensacion);
    }

    @Transactional
    public Optional<TrabajoCompensacion> reclamarSiguiente(String trabajador) {
        OffsetDateTime ahora = OffsetDateTime.ofInstant(
            Instantes.ahora(), ZoneOffset.UTC);
        OffsetDateTime limite = ahora.minus(
            propiedades.reclamacionVencida());

        Optional<IntentoCompensacion> reclamada =
            compensacionRepositorio.reclamarSiguiente(
                limite, ahora, propiedades.maxIntentos());
        if (reclamada.isEmpty()) {
            return Optional.empty();
        }

        IntentoCompensacion compensacion = reclamada.get();
        if (compensacion.getEstado() == EstadoIntentoCompensacion.PENDIENTE) {
            compensacion.reclamar(trabajador);
        } else {
            compensacion.reclamarRecuperacion(trabajador);
        }
        compensacionRepositorio.flush();
        return Optional.of(trabajo(compensacion));
    }

    @Transactional
    public TrabajoCompensacion reclamarParaReintentoManual(
        UUID idCompensacion,
        String trabajador
    ) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.reintentarManual(trabajador);
        compensacionRepositorio.flush();
        return trabajo(compensacion);
    }

    @Transactional
    public void marcarDespachoAbortado(UUID revisionId, String detalle) {
        compensacionRepositorio
            .buscarPorRevisionIdParaActualizar(revisionId)
            .ifPresent(compensacion -> {
                compensacion.marcarDespachoAbortado(detalle);
                compensacionRepositorio.flush();
            });
    }

    @Transactional
    public void marcarPendiente(
        UUID idCompensacion,
        String error,
        Instant proximoIntento
    ) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarPendiente(error, proximoIntento);
        compensacionRepositorio.flush();
    }

    @Transactional
    public void marcarLiberada(UUID idCompensacion, String referencia) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarLiberada(referencia);
        compensacionRepositorio.flush();
    }

    @Transactional
    public void marcarCanceladaSinReserva(
        UUID idCompensacion,
        String detalle
    ) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarCanceladaSinReserva(detalle);
        compensacionRepositorio.flush();
    }

    @Transactional
    public void marcarRechazada(UUID idCompensacion, String detalle) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarRechazada(detalle);
        compensacionRepositorio.flush();
    }

    @Transactional
    public void marcarFallida(UUID idCompensacion, String error) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarFallida(error);
        compensacionRepositorio.flush();
    }

    @Transactional
    public void marcarRevisionManual(UUID idCompensacion, String error) {
        IntentoCompensacion compensacion = bloquear(idCompensacion);
        compensacion.marcarRevisionManual(error);
        compensacionRepositorio.flush();
    }

    @Transactional(readOnly = true)
    public IntentoCompensacion obtener(UUID idCompensacion) {
        return compensacionRepositorio.findById(idCompensacion)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la compensación " + idCompensacion));
    }

    private IntentoCompensacion bloquear(UUID idCompensacion) {
        return compensacionRepositorio.buscarPorIdParaActualizar(idCompensacion)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la compensación " + idCompensacion));
    }

    private TrabajoCompensacion trabajo(IntentoCompensacion compensacion) {
        RevisionSolicitud revision = compensacion.getRevision();
        IntentoEjecucion intento = intentoEjecucionRepositorio
            .buscarPorRevisionId(revision.getId())
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la intención de ejecución de la revisión "
                    + revision.getId()));
        return new TrabajoCompensacion(
            compensacion.getId(),
            compensacion.getSolicitud().getId(),
            revision.getId(),
            intento.getId(),
            revision.getIdentificadorProceso(),
            compensacion.getClave(),
            compensacion.getHuella(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            compensacion.getEstado(),
            compensacion.getIntentos(),
            compensacion.isDespachoEnCurso()
        );
    }
}
