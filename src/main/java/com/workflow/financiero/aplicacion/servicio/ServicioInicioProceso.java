package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.evento.TipoEventoSolicitud;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.riesgo.ClasificadorRiesgoInterno;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudFinancieraRepositorio;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicia el proceso de aprobación de una revisión y deja registrada la
 * instancia en la misma transacción que crea la solicitud.
 */
@Service
public class ServicioInicioProceso {

    public static final String CLAVE_PROCESO = "aprobacionFinanciera";

    private final SolicitudFinancieraRepositorio solicitudRepositorio;
    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final CalculadoraPlanAprobacion calculadoraPlan;
    private final ServicioEventoSolicitud servicioEvento;
    private final PuertoProceso puertoProceso;
    private final PropiedadesAprobacion propiedadesAprobacion;
    private final SincronizadorTareasAprobacion sincronizadorTareas;

    public ServicioInicioProceso(
        SolicitudFinancieraRepositorio solicitudRepositorio,
        RevisionSolicitudRepositorio revisionRepositorio,
        CalculadoraPlanAprobacion calculadoraPlan,
        ServicioEventoSolicitud servicioEvento,
        PuertoProceso puertoProceso,
        PropiedadesAprobacion propiedadesAprobacion,
        SincronizadorTareasAprobacion sincronizadorTareas
    ) {
        this.solicitudRepositorio = solicitudRepositorio;
        this.revisionRepositorio = revisionRepositorio;
        this.calculadoraPlan = calculadoraPlan;
        this.servicioEvento = servicioEvento;
        this.puertoProceso = puertoProceso;
        this.propiedadesAprobacion = propiedadesAprobacion;
        this.sincronizadorTareas = sincronizadorTareas;
    }

    @Transactional
    public String iniciar(UUID solicitudId, UUID revisionId, String correlacion) {
        if (solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias para iniciar el proceso");
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
        RevisionSolicitud revisionActual = solicitud.getRevisionActual();
        if (revisionActual == null || !revisionActual.getId().equals(revisionId)) {
            throw new TransicionEstadoInvalidaException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (revision.getIdentificadorProceso() != null
            && !revision.getIdentificadorProceso().isBlank()) {
            throw new IllegalStateException("La revisión ya tiene un proceso asociado");
        }
        if (revision.getEstado() != EstadoRevision.EVALUANDO_RIESGO) {
            throw new TransicionEstadoInvalidaException(
                "No se puede iniciar el proceso en estado " + revision.getEstado());
        }
        if (revision.getNivelRiesgo() == null) {
            throw new TransicionEstadoInvalidaException(
                "La revisión no tiene el riesgo clasificado");
        }

        PlanAprobacion plan = calculadoraPlan.calcular(
            new ContextoAprobacion(revision.getMonto(), revision.getNivelRiesgo()));

        Map<String, Object> variables = variablesProceso(
            solicitud, revision, plan, correlacion);

        String identificador = puertoProceso.iniciar(
            CLAVE_PROCESO,
            revisionId.toString(),
            variables
        );

        revision.registrarIdentificadorProceso(identificador);
        revisionRepositorio.saveAndFlush(revision);

        sincronizadorTareas.sincronizar(solicitudId, revisionId, identificador);

        servicioEvento.registrar(
            solicitud,
            revision,
            TipoEventoSolicitud.PROCESO_INICIADO,
            null,
            null,
            null,
            null,
            correlacion,
            "Proceso BPMN iniciado"
        );

        return identificador;
    }

    private Map<String, Object> variablesProceso(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        PlanAprobacion plan,
        String correlacion
    ) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", solicitud.getId().toString());
        variables.put("revisionId", revision.getId().toString());
        variables.put("numeroRevision", revision.getNumero());
        variables.put("rolesPlan", plan.roles().stream().sorted().toList());
        variables.put("requiereAprobacion", !plan.estaVacio());
        variables.put("requiereSupervisor", plan.requiere("SUPERVISOR"));
        variables.put("requiereGerente", plan.requiere("GERENTE"));
        variables.put("requiereRiesgos", plan.requiere("RIESGOS"));
        variables.put("nivelRiesgo", revision.getNivelRiesgo().name());
        variables.put(
            "requiereEvaluacionExterna",
            ClasificadorRiesgoInterno.requiereEvaluacionExterna(revision.getMonto())
        );
        variables.put(
            "duracionNotificacion",
            propiedadesAprobacion.notificacion().toString());
        variables.put(
            "duracionReasignacion",
            propiedadesAprobacion.reasignacion().toString());
        variables.put("correlacion", correlacion == null ? "" : correlacion);
        return variables;
    }
}
