package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.ServicioReasignacionTarea;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/**
 * Traduce el temporizador de cada rama a la tarea de negocio pendiente y
 * delega la reasignación al superior configurado.
 */
@Component("delegadoReasignarTarea")
@RequiredArgsConstructor
@Slf4j
public class DelegadoReasignarTarea implements JavaDelegate {

    private static final Map<String, String> TAREAS_POR_ACTIVIDAD = Map.of(
        "reasignarSupervisor", "tareaSupervisor",
        "reasignarGerente", "tareaGerente",
        "reasignarRiesgos", "tareaRiesgos"
    );

    private final TaskService taskService;
    private final ServicioReasignacionTarea servicioReasignacion;

    @Override
    public void execute(DelegateExecution ejecucion) {
        String definicion = TAREAS_POR_ACTIVIDAD.get(
            ejecucion.getCurrentActivityId());
        if (definicion == null) {
            log.warn(
                "Actividad de reasignación desconocida [proceso={}, actividad={}]",
                ejecucion.getProcessInstanceId(),
                ejecucion.getCurrentActivityId()
            );
            return;
        }

        Task tarea = taskService.createTaskQuery()
            .processInstanceId(ejecucion.getProcessInstanceId())
            .taskDefinitionKey(definicion)
            .singleResult();
        if (tarea == null) {
            log.info(
                "La tarea {} ya no está activa; no se reasigna [proceso={}]",
                definicion,
                ejecucion.getProcessInstanceId()
            );
            return;
        }

        servicioReasignacion.reasignar(
            tarea.getId(),
            VariablesProceso.texto(ejecucion, "correlacion")
        );
    }
}
