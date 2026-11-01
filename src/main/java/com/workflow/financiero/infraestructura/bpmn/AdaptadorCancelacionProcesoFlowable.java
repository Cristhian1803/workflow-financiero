package com.workflow.financiero.infraestructura.bpmn;

import com.workflow.financiero.aplicacion.puerto.PuertoCancelacionProceso;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.stereotype.Component;

/**
 * Cancela la instancia del proceso. Se prefiere el subproceso de eventos para
 * interrumpir tareas y timers; si la instancia no tiene esa suscripción, se
 * elimina de forma controlada. La compensación financiera ya ocurrió antes de
 * llegar aquí.
 */
@Component
@RequiredArgsConstructor
public class AdaptadorCancelacionProcesoFlowable
    implements PuertoCancelacionProceso {

    public static final String MENSAJE_CANCELACION_SOLICITADA =
        "CANCELACION_SOLICITADA";

    private final RuntimeService runtimeService;

    @Override
    public void solicitarCancelacion(String idProceso, String motivo) {
        if (idProceso == null || idProceso.isBlank()) {
            return;
        }

        Execution ejecucion = runtimeService.createExecutionQuery()
            .processInstanceId(idProceso)
            .messageEventSubscriptionName(MENSAJE_CANCELACION_SOLICITADA)
            .singleResult();
        if (ejecucion != null) {
            runtimeService.messageEventReceived(
                MENSAJE_CANCELACION_SOLICITADA,
                ejecucion.getId(),
                Map.of("motivo", motivo == null ? "" : motivo)
            );
            return;
        }

        if (runtimeService.createProcessInstanceQuery()
            .processInstanceId(idProceso)
            .count() > 0) {
            runtimeService.deleteProcessInstance(
                idProceso,
                motivo == null || motivo.isBlank()
                    ? "Cancelación solicitada"
                    : motivo
            );
        }
    }
}
