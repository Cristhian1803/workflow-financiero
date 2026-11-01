package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
import com.workflow.financiero.dominio.modelo.EventoSolicitudPrueba;
import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudPruebaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudPruebaRepositorio;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones que escriben en JPA y Flowable dentro de la misma transaccion.
 * Verifican commit y rollback al iniciar un proceso y al completar una tarea.
 */
@Service
@RequiredArgsConstructor
public class ServicioTransaccionMotorPrueba {

    public static final String CLAVE_PROCESO_TRANSACCION = "procesoTransaccionPrueba";

    private final SolicitudPruebaRepositorio solicitudRepositorio;
    private final EventoSolicitudPruebaRepositorio eventoRepositorio;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    @Transactional
    public UUID iniciarSolicitudYProceso(UUID solicitudId, boolean fallarDespues) {
        SolicitudPrueba solicitud = new SolicitudPrueba();
        solicitud.setId(solicitudId);
        solicitud.setEstado("INICIADA");
        solicitud.setMonto(1_000_000L);
        solicitud.setMoneda("PYG");
        solicitudRepositorio.save(solicitud);

        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", solicitud.getId().toString());
        ProcessInstance instancia = runtimeService.startProcessInstanceByKey(CLAVE_PROCESO_TRANSACCION, variables);
        SolicitudPrueba actualizada = solicitudRepositorio.findById(solicitudId).orElseThrow();
        actualizada.setIdentificadorInstanciaProceso(instancia.getId());
        solicitudRepositorio.save(actualizada);

        if (fallarDespues) {
            throw new ErrorSimuladoPrueba();
        }
        return solicitud.getId();
    }

    @Transactional
    public void registrarEventoYCompletarTarea(UUID solicitudId, boolean fallarDespues) {
        SolicitudPrueba solicitud = solicitudRepositorio.findById(solicitudId)
            .orElseThrow(() -> new IllegalStateException("No existe la solicitud indicada"));

        EventoSolicitudPrueba evento = new EventoSolicitudPrueba();
        evento.setId(UUID.randomUUID());
        evento.setSolicitudId(solicitudId);
        evento.setTipo("DECISION_APROBACION");
        evento.setDetalle("Tarea de aprobación completada");
        eventoRepositorio.save(evento);

        Task tarea = taskService.createTaskQuery()
            .processInstanceId(solicitud.getIdentificadorInstanciaProceso())
            .singleResult();
        if (tarea == null) {
            throw new IllegalStateException("La solicitud no tiene tareas pendientes");
        }
        taskService.complete(tarea.getId());
        solicitud.setEstado("TAREA_COMPLETADA");
        solicitudRepositorio.save(solicitud);

        if (fallarDespues) {
            throw new ErrorSimuladoPrueba();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SolicitudPrueba> buscarSolicitud(UUID id) {
        return solicitudRepositorio.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long contarEventos(UUID solicitudId) {
        return eventoRepositorio.countBySolicitudId(solicitudId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long contarInstanciasActivasPorSolicitud(UUID solicitudId) {
        return runtimeService.createProcessInstanceQuery()
            .variableValueEquals("solicitudId", solicitudId.toString())
            .active()
            .count();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long contarTareasActivasPorSolicitud(UUID solicitudId) {
        return taskService.createTaskQuery()
            .processVariableValueEquals("solicitudId", solicitudId.toString())
            .active()
            .count();
    }
}
