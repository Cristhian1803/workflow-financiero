package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.aplicacion.puerto.TareaMotor;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.riesgo.ClasificadorRiesgoInterno;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de solicitudes visibles. Devuelve read models y nunca entidades
 * JPA al controlador.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioConsultaSolicitud {

    private static final Sort ORDEN = Sort.by(
        Sort.Order.desc("creadoEn"),
        Sort.Order.asc("id")
    );

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final EventoSolicitudRepositorio eventoRepositorio;
    private final ServicioAutorizacionConsulta autorizacion;
    private final CalculadoraPlanAprobacion calculadoraPlan;
    private final PuertoProceso puertoProceso;

    public SolicitudDetalle obtener(UUID id, UsuarioActual actual) {
        SolicitudFinanciera solicitud = buscarVisible(id);
        autorizacion.verificarDetalle(solicitud, actual);
        return detalle(solicitud);
    }

    public PaginaSolicitudes<SolicitudResumen> listar(
        int pagina,
        int tamano,
        EstadoRevision estado,
        UsuarioActual actual
    ) {
        autorizacion.verificarListado(actual);

        UUID solicitanteId = actual.tieneRol("ADMINISTRADOR")
            ? null
            : actual.id();

        Page<SolicitudFinanciera> resultado = solicitudRepositorio.buscarVisibles(
            solicitanteId,
            estado,
            PageRequest.of(pagina, tamano, ORDEN)
        );

        return new PaginaSolicitudes<>(
            resultado.getContent().stream().map(this::resumen).toList(),
            pagina,
            tamano,
            resultado.getTotalElements(),
            resultado.getTotalPages()
        );
    }

    public List<EventoHistorial> historial(UUID id, UsuarioActual actual) {
        SolicitudFinanciera solicitud = buscarVisible(id);
        autorizacion.verificarHistorial(solicitud, actual);

        return eventoRepositorio.buscarPorSolicitud(id).stream()
            .map(evento -> new EventoHistorial(
                evento.getId(),
                evento.getRevision().getNumero(),
                evento.getTipo(),
                evento.getActor() == null ? null : evento.getActor().getUsuario(),
                evento.getRol() == null ? null : evento.getRol().getCodigo(),
                evento.getEstadoAnterior(),
                evento.getEstadoNuevo(),
                evento.getDetalle(),
                evento.getCorrelacion(),
                evento.getCreadoEn()
            ))
            .toList();
    }

    public EstadoProceso proceso(UUID id, UsuarioActual actual) {
        SolicitudFinanciera solicitud = buscarVisible(id);
        autorizacion.verificarDetalle(solicitud, actual);

        RevisionSolicitud revision = revisionActual(solicitud);
        String identificadorProceso = revision.getIdentificadorProceso();
        boolean iniciado = identificadorProceso != null
            && !identificadorProceso.isBlank();

        if (!iniciado) {
            return new EstadoProceso(
                solicitud.getId(),
                revision.getNumero(),
                null,
                false,
                false,
                revision.getEstado(),
                List.of(),
                List.of()
            );
        }

        boolean activo = puertoProceso.estaActivo(identificadorProceso);
        boolean finalizado = puertoProceso.estaFinalizado(identificadorProceso);
        List<String> actividades = activo
            ? puertoProceso.actividadesActivas(identificadorProceso)
            : List.of();
        List<TareaProceso> tareas = activo
            ? puertoProceso.tareasActivas(identificadorProceso).stream()
                .map(this::tarea)
                .toList()
            : List.of();

        return new EstadoProceso(
            solicitud.getId(),
            revision.getNumero(),
            identificadorProceso,
            true,
            finalizado,
            revision.getEstado(),
            actividades,
            tareas
        );
    }

    private TareaProceso tarea(TareaMotor tarea) {
        String rol = tarea.gruposCandidatos().isEmpty()
            ? null
            : tarea.gruposCandidatos().getFirst();
        return new TareaProceso(
            tarea.id(),
            tarea.definicion(),
            tarea.nombre(),
            "PENDIENTE",
            tarea.asignadoA(),
            rol,
            null
        );
    }

    private SolicitudFinanciera buscarVisible(UUID id) {
        return solicitudRepositorio.buscarDetallePorId(id)
            .orElseThrow(SolicitudNoEncontradaException::new);
    }

    private SolicitudDetalle detalle(SolicitudFinanciera solicitud) {
        RevisionSolicitud revision = revisionActual(solicitud);
        List<String> plan = revision.getNivelRiesgo() == null
            ? List.of()
            : calculadoraPlan.calcular(new ContextoAprobacion(
                    revision.getMonto(), revision.getNivelRiesgo()))
                .roles()
                .stream()
                .sorted()
                .toList();

        return new SolicitudDetalle(
            solicitud.getId(),
            revision.getId(),
            revision.getNumero(),
            solicitud.getSolicitante().getUsuario(),
            revision.getTipoOperacion().getCodigo(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getDescripcion(),
            revision.getJustificacion(),
            revision.getEstado(),
            revision.getNivelRiesgo(),
            plan,
            ClasificadorRiesgoInterno.requiereEvaluacionExterna(revision.getMonto()),
            solicitud.getCreadoEn(),
            solicitud.getActualizadoEn() == null
                ? solicitud.getCreadoEn()
                : solicitud.getActualizadoEn()
        );
    }

    private SolicitudResumen resumen(SolicitudFinanciera solicitud) {
        RevisionSolicitud revision = revisionActual(solicitud);
        return new SolicitudResumen(
            solicitud.getId(),
            revision.getNumero(),
            solicitud.getSolicitante().getUsuario(),
            revision.getTipoOperacion().getCodigo(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getEstado(),
            revision.getNivelRiesgo(),
            solicitud.getCreadoEn()
        );
    }

    private RevisionSolicitud revisionActual(SolicitudFinanciera solicitud) {
        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null) {
            throw new IllegalStateException(
                "La solicitud " + solicitud.getId() + " no tiene revisión actual");
        }
        return revision;
    }
}
