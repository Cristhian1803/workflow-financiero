package com.workflow.financiero.infraestructura.bpmn;

import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.aplicacion.puerto.TareaMotor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/**
 * Adaptador de Flowable para el puerto de procesos.
 */
@Component
@RequiredArgsConstructor
public class AdaptadorProcesoFlowable implements PuertoProceso {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    @Override
    public String iniciar(
        String claveProceso,
        String claveNegocio,
        Map<String, Object> variables
    ) {
        ProcessInstance instancia = runtimeService.startProcessInstanceByKey(
            claveProceso,
            claveNegocio,
            variables == null ? Map.of() : variables
        );
        return instancia.getId();
    }

    @Override
    public void completarTarea(String idTareaMotor, Map<String, Object> variables) {
        taskService.complete(
            idTareaMotor,
            variables == null ? Map.of() : variables
        );
    }

    @Override
    public void asignarTarea(String idTareaMotor, String usuario) {
        taskService.setAssignee(idTareaMotor, usuario);
    }

    @Override
    public List<TareaMotor> tareasActivas(String idProceso) {
        if (idProceso == null || idProceso.isBlank()) {
            return List.of();
        }

        return taskService.createTaskQuery()
            .processInstanceId(idProceso)
            .orderByTaskCreateTime()
            .asc()
            .list()
            .stream()
            .map(this::tarea)
            .toList();
    }

    @Override
    public List<String> actividadesActivas(String idProceso) {
        if (!estaActivo(idProceso)) {
            return List.of();
        }

        // Flowable también informa los temporizadores de frontera como
        // actividades activas. La API solo expone las tareas humanas.
        List<String> tareas = taskService.createTaskQuery()
            .processInstanceId(idProceso)
            .list()
            .stream()
            .map(Task::getTaskDefinitionKey)
            .toList();
        return runtimeService.getActiveActivityIds(idProceso).stream()
            .filter(tareas::contains)
            .distinct()
            .toList();
    }

    @Override
    public boolean estaActivo(String idProceso) {
        if (idProceso == null || idProceso.isBlank()) {
            return false;
        }
        return runtimeService.createProcessInstanceQuery()
            .processInstanceId(idProceso)
            .count() > 0;
    }

    @Override
    public boolean estaFinalizado(String idProceso) {
        if (idProceso == null || idProceso.isBlank()) {
            return false;
        }

        HistoricProcessInstance historica = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(idProceso)
            .singleResult();
        return historica != null && historica.getEndTime() != null;
    }

    @Override
    public void enviarMensaje(
        String nombreMensaje,
        String idProceso,
        Map<String, Object> variables
    ) {
        if (idProceso == null || idProceso.isBlank()) {
            throw new IllegalArgumentException(
                "El proceso es obligatorio para enviar el mensaje");
        }

        // El mensaje se correlaciona con la ejecución que tiene la suscripción,
        // no con el identificador de la instancia del proceso.
        Execution ejecucion = runtimeService.createExecutionQuery()
            .processInstanceId(idProceso)
            .messageEventSubscriptionName(nombreMensaje)
            .singleResult();
        if (ejecucion == null) {
            throw new IllegalStateException(
                "El proceso " + idProceso + " no espera el mensaje "
                    + nombreMensaje);
        }

        runtimeService.messageEventReceived(
            nombreMensaje,
            ejecucion.getId(),
            variables == null ? Map.of() : variables
        );
    }

    private TareaMotor tarea(Task tarea) {
        List<String> grupos = taskService.getIdentityLinksForTask(tarea.getId())
            .stream()
            .filter(enlace -> IdentityLinkType.CANDIDATE.equals(enlace.getType()))
            .map(IdentityLink::getGroupId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

        return new TareaMotor(
            tarea.getId(),
            tarea.getTaskDefinitionKey(),
            tarea.getName(),
            tarea.getAssignee(),
            grupos,
            tarea.getCreateTime() == null ? null : tarea.getCreateTime().toInstant()
        );
    }
}
