package com.workflow.financiero.infraestructura.bpmn.delegado;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Stub controlado para que los temporizadores no fallen mientras la
 * reasignación real no esté implementada.
 */
@Component("delegadoReasignarTarea")
@Slf4j
public class DelegadoReasignarTarea implements JavaDelegate {

    @Override
    public void execute(DelegateExecution ejecucion) {
        log.info(
            "Reasignación pendiente [proceso={}, actividad={}]",
            ejecucion.getProcessInstanceId(),
            ejecucion.getCurrentActivityId()
        );
    }
}
