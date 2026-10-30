package com.workflow.financiero.infraestructura.bpmn.delegado;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Stub controlado para que los temporizadores no fallen mientras la
 * notificación real no esté implementada.
 */
@Component("delegadoNotificarEscalamiento")
@Slf4j
public class DelegadoNotificarEscalamiento implements JavaDelegate {

    @Override
    public void execute(DelegateExecution ejecucion) {
        log.info(
            "Notificación de escalamiento pendiente [proceso={}, actividad={}]",
            ejecucion.getProcessInstanceId(),
            ejecucion.getCurrentActivityId()
        );
    }
}
