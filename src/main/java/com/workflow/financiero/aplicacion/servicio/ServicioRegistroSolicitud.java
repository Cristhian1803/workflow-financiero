package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.modelo.Usuario;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
import com.workflow.financiero.dominio.regla.validacion.ContextoValidacion;
import com.workflow.financiero.dominio.regla.validacion.SolicitudAValidar;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la solicitud y su primera revisión, aplica validación, riesgo y plan y
 * deja la revisión en EVALUANDO_RIESGO. No inicia Flowable ni abre tareas.
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
    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioClasificacionRiesgo servicioClasificacion;
    private final CalculadoraPlanAprobacion calculadoraPlan;

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

        servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.VALIDANDO,
            new ContextoTransicion(
                null, null, correlacion, "Validación de entrada")
        );

        RevisionSolicitud evaluando = servicioTransicion.transicionar(
            solicitud.getId(),
            revision.getId(),
            EstadoRevision.EVALUANDO_RIESGO,
            new ContextoTransicion(
                null, null, correlacion, "Evaluación de riesgo iniciada")
        );

        ResultadoRiesgo riesgo = servicioClasificacion.clasificarYRegistrar(
            solicitud.getId(), revision.getId(), correlacion);

        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(evaluando.getMonto(), riesgo.nivel()));

        servicioEvento.registrar(
            solicitud,
            evaluando,
            TipoEventoSolicitud.PLAN_APROBACION_CALCULADO,
            null,
            null,
            null,
            null,
            correlacion,
            detallePlan(plan)
        );

        if (riesgo.requiereEvaluacionExterna()) {
            servicioEvento.registrar(
                solicitud,
                evaluando,
                TipoEventoSolicitud.EVALUACION_EXTERNA_PENDIENTE,
                null,
                null,
                null,
                null,
                correlacion,
                "Evaluación externa pendiente por monto superior a 200000000"
            );
        }

        return new SolicitudDetalle(
            solicitud.getId(),
            evaluando.getId(),
            evaluando.getNumero(),
            solicitante.getUsuario(),
            evaluando.getTipoOperacion().getCodigo(),
            evaluando.getMonto(),
            evaluando.getMoneda().getCodigo(),
            evaluando.getDescripcion(),
            evaluando.getJustificacion(),
            evaluando.getEstado(),
            riesgo.nivel(),
            rolesOrdenados(plan),
            riesgo.requiereEvaluacionExterna(),
            solicitud.getCreadoEn(),
            solicitud.getActualizadoEn() == null
                ? solicitud.getCreadoEn()
                : solicitud.getActualizadoEn()
        );
    }

    private List<String> rolesOrdenados(PlanAprobacion plan) {
        return plan.roles().stream().sorted().toList();
    }

    private String detallePlan(PlanAprobacion plan) {
        if (plan.estaVacio()) {
            return "Plan de aprobación calculado: sin aprobaciones";
        }
        return "Plan de aprobación calculado: "
            + String.join(", ", rolesOrdenados(plan));
    }
}
