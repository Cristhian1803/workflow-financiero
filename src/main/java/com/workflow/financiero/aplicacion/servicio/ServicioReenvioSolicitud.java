package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.EventoSolicitud;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.ReenvioNoPermitidoException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.validacion.ContextoValidacion;
import com.workflow.financiero.dominio.regla.validacion.SolicitudAValidar;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea una revisión nueva a partir de una solicitud rechazada, recalcula el
 * riesgo y el plan e inicia un proceso independiente. La revisión anterior
 * conserva su historial.
 */
@Service
@RequiredArgsConstructor
public class ServicioReenvioSolicitud {

    private static final int MAXIMO_REVISIONES = 3;
    private static final Duration PLAZO_REENVIO = Duration.ofDays(30);
    private static final String ROL_SOLICITANTE = "SOLICITANTE";

    private final ServicioValidacionSolicitud servicioValidacion;
    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final RolRepositorio rolRepositorio;
    private final EventoSolicitudRepositorio eventoRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioPreparacionRevision servicioPreparacion;
    private final Clock reloj;

    @Transactional
    public SolicitudDetalle reenviar(
        UUID solicitudId,
        UsuarioActual actual,
        CrearSolicitudComando comando,
        String correlacion
    ) {
        if (actual == null || comando == null) {
            throw new IllegalArgumentException(
                "El solicitante y los datos corregidos son obligatorios");
        }

        // El bloqueo serializa dos reenvíos concurrentes sobre la misma solicitud.
        SolicitudFinanciera solicitud = solicitudRepositorio
            .buscarPorIdParaActualizar(solicitudId)
            .orElseThrow(SolicitudNoEncontradaException::new);

        verificarPropietario(solicitud, actual);

        RevisionSolicitud anterior = solicitud.getRevisionActual();
        if (anterior == null) {
            throw new TransicionEstadoInvalidaException(
                "La solicitud no tiene una revisión actual");
        }
        if (anterior.getEstado() != EstadoRevision.RECHAZADA) {
            throw new TransicionEstadoInvalidaException(
                "La revisión actual no está rechazada");
        }

        int siguienteNumero = anterior.getNumero() + 1;
        if (siguienteNumero > MAXIMO_REVISIONES) {
            throw new ReenvioNoPermitidoException(
                "La solicitud alcanzó el máximo de "
                    + MAXIMO_REVISIONES + " revisiones");
        }

        verificarPlazo(anterior, Instant.now(reloj));

        ContextoValidacion contexto = servicioValidacion.validar(
            new SolicitudAValidar(
                actual.id(),
                comando.tipoOperacion(),
                comando.monto(),
                comando.moneda(),
                comando.descripcion(),
                comando.justificacion()
            )
        );

        RevisionSolicitud nueva = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                siguienteNumero,
                contexto.tipoOperacion(),
                contexto.monto(),
                contexto.moneda(),
                comando.descripcion(),
                comando.justificacion()
            )
        );

        solicitud.establecerRevisionActual(nueva);
        solicitudRepositorio.saveAndFlush(solicitud);

        Rol rolSolicitante = rolRepositorio.findById(ROL_SOLICITANTE)
            .orElseThrow(() -> new IllegalStateException(
                "No existe el rol " + ROL_SOLICITANTE));

        servicioEvento.registrar(
            solicitud,
            nueva,
            TipoEventoSolicitud.REVISION_REENVIADA,
            contexto.solicitante(),
            rolSolicitante,
            null,
            null,
            correlacion,
            detalleReenvio(anterior, nueva)
        );

        PreparacionRevision preparacion = servicioPreparacion.preparar(
            solicitud, nueva, correlacion);
        RevisionSolicitud preparada = preparacion.revision();

        return new SolicitudDetalle(
            solicitud.getId(),
            preparada.getId(),
            preparada.getNumero(),
            solicitud.getSolicitante().getUsuario(),
            preparada.getTipoOperacion().getCodigo(),
            preparada.getMonto(),
            preparada.getMoneda().getCodigo(),
            preparada.getDescripcion(),
            preparada.getJustificacion(),
            preparada.getEstado(),
            preparacion.riesgo().nivel(),
            preparacion.roles(),
            preparacion.riesgo().requiereEvaluacionExterna(),
            solicitud.getCreadoEn(),
            solicitud.getActualizadoEn() == null
                ? solicitud.getCreadoEn()
                : solicitud.getActualizadoEn()
        );
    }

    private void verificarPropietario(
        SolicitudFinanciera solicitud,
        UsuarioActual actual
    ) {
        if (solicitud.getSolicitante().getId().equals(actual.id())) {
            return;
        }
        // Un solicitante sin visibilidad recibe 404; los demás roles reciben 403.
        if (actual.tieneRol("ADMINISTRADOR")
            || !actual.tieneRol("SOLICITANTE")) {
            throw new AccesoDenegadoException(
                "Solo el dueño puede reenviar la solicitud");
        }
        throw new SolicitudNoEncontradaException();
    }

    private void verificarPlazo(RevisionSolicitud revision, Instant ahora) {
        Instant limite = fechaRechazo(revision).plus(PLAZO_REENVIO);
        if (ahora.isAfter(limite)) {
            throw new ReenvioNoPermitidoException(
                "La solicitud superó el plazo de 30 días para reenviar");
        }
    }

    private Instant fechaRechazo(RevisionSolicitud revision) {
        if (revision.getActualizadoEn() != null) {
            return revision.getActualizadoEn();
        }

        // Respaldo para datos anteriores a la transición de rechazo.
        return eventoRepositorio.buscarPorRevision(revision.getId()).stream()
            .filter(evento ->
                evento.getTipo() == TipoEventoSolicitud.RECHAZO_REGISTRADO)
            .map(EventoSolicitud::getCreadoEn)
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new TransicionEstadoInvalidaException(
                "No se pudo determinar la fecha del rechazo"));
    }

    private String detalleReenvio(
        RevisionSolicitud anterior,
        RevisionSolicitud nueva
    ) {
        return "Revisión " + nueva.getNumero()
            + " creada por reenvío de la revisión " + anterior.getNumero()
            + ": " + nueva.getTipoOperacion().getCodigo()
            + ", monto " + nueva.getMonto().toPlainString()
            + " " + nueva.getMoneda().getCodigo();
    }
}
