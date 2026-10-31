package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.riesgo.ResultadoRiesgo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completa una revisión recién creada: valida, clasifica el riesgo, calcula el
 * plan, registra el historial e inicia el proceso. La creación y el reenvío
 * comparten esta secuencia.
 */
@Service
@RequiredArgsConstructor
public class ServicioPreparacionRevision {

    private final ServicioTransicionRevision servicioTransicion;
    private final ServicioClasificacionRiesgo servicioClasificacion;
    private final CalculadoraPlanAprobacion calculadoraPlan;
    private final ServicioEventoSolicitud servicioEvento;
    private final ServicioInicioProceso servicioInicioProceso;

    @Transactional
    public PreparacionRevision preparar(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        String correlacion
    ) {
        if (solicitud == null || revision == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias para preparar el proceso");
        }

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
            solicitud.getId(), evaluando.getId(), correlacion);

        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(evaluando.getMonto(), riesgo.nivel()));

        List<String> roles = plan.roles().stream().sorted().toList();

        servicioEvento.registrar(
            solicitud,
            evaluando,
            TipoEventoSolicitud.PLAN_APROBACION_CALCULADO,
            null,
            null,
            null,
            null,
            correlacion,
            detallePlan(roles)
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

        servicioInicioProceso.iniciar(
            solicitud.getId(),
            evaluando.getId(),
            correlacion
        );

        return new PreparacionRevision(evaluando, riesgo, roles);
    }

    private String detallePlan(List<String> roles) {
        if (roles.isEmpty()) {
            return "Plan de aprobación calculado: sin aprobaciones";
        }
        return "Plan de aprobación calculado: " + String.join(", ", roles);
    }
}
