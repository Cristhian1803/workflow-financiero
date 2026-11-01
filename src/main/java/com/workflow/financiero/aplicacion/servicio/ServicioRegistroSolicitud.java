package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.validacion.ContextoValidacion;
import com.workflow.financiero.dominio.regla.validacion.SolicitudAValidar;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la solicitud y su primera revisión, aplica validación, riesgo y plan e
 * inicia el proceso de aprobación en la misma transacción.
 */
@Service
@RequiredArgsConstructor
public class ServicioRegistroSolicitud {

    private static final String ROL_SOLICITANTE = "SOLICITANTE";

    private final ServicioValidacionSolicitud servicioValidacion;
    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final RolRepositorio rolRepositorio;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioPreparacionRevision servicioPreparacion;

    @Transactional
    public SolicitudDetalle registrar(
        UsuarioActual solicitanteActual,
        CrearSolicitudComando comando,
        String correlacion
    ) {
        if (solicitanteActual == null || comando == null) {
            throw new IllegalArgumentException(
                "El solicitante y los datos de la solicitud son obligatorios");
        }

        ContextoValidacion contexto = servicioValidacion.validar(new SolicitudAValidar(
            solicitanteActual.id(),
            comando.tipoOperacion(),
            comando.monto(),
            comando.moneda(),
            comando.descripcion(),
            comando.justificacion()
        ));

        Usuario solicitante = contexto.solicitante();
        SolicitudFinanciera solicitud = solicitudRepositorio.save(
            new SolicitudFinanciera(solicitante));

        RevisionSolicitud revision = revisionRepositorio.saveAndFlush(
            new RevisionSolicitud(
                solicitud,
                1,
                contexto.tipoOperacion(),
                contexto.monto(),
                contexto.moneda(),
                comando.descripcion(),
                comando.justificacion()
            )
        );

        solicitud.establecerRevisionActual(revision);
        solicitudRepositorio.saveAndFlush(solicitud);

        Rol rolSolicitante = rolRepositorio.findById(ROL_SOLICITANTE)
            .orElseThrow(() -> new IllegalStateException(
                "No existe el rol " + ROL_SOLICITANTE));

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.SOLICITUD_CREADA,
            solicitante,
            rolSolicitante,
            null,
            EstadoRevision.RECIBIDA,
            correlacion,
            "Solicitud registrada"
        );

        PreparacionRevision preparacion = servicioPreparacion.preparar(
            solicitud, revision, correlacion);
        RevisionSolicitud preparada = preparacion.revision();

        return new SolicitudDetalle(
            solicitud.getId(),
            preparada.getId(),
            preparada.getNumero(),
            solicitante.getUsuario(),
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
}
