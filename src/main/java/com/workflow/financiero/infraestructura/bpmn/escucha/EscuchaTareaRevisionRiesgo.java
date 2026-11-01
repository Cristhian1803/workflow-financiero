package com.workflow.financiero.infraestructura.bpmn.escucha;

import com.workflow.financiero.aplicacion.servicio.ServicioTareaRevisionRiesgo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * Copia la userTask de revisión manual a la tabla de negocio cuando Flowable
 * la crea. La operación es idempotente para tolerar reintentos del motor.
 */
@Component("escuchaTareaRevisionRiesgo")
@RequiredArgsConstructor
public class EscuchaTareaRevisionRiesgo implements TaskListener {

    private final ServicioTareaRevisionRiesgo servicio;

    @Override
    public void notify(DelegateTask tarea) {
        Object valor = tarea.getVariable("revisionId");
        if (valor == null) {
            throw new IllegalStateException(
                "La tarea manual no tiene la revisión asociada");
        }
        servicio.registrarDesdeMotor(
            UUID.fromString(valor.toString()),
            tarea.getId()
        );
    }
}
