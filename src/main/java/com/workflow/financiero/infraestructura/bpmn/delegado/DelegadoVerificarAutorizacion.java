package com.workflow.financiero.infraestructura.bpmn.delegado;

import com.workflow.financiero.aplicacion.servicio.VerificadorAutorizacionEjecucion;
import com.workflow.financiero.dominio.regla.aprobacion.ResultadoAutorizacion;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("delegadoVerificarAutorizacion")
@RequiredArgsConstructor
public class DelegadoVerificarAutorizacion implements JavaDelegate {

    private final VerificadorAutorizacionEjecucion verificador;

    @Override
    public void execute(DelegateExecution ejecucion) {
        ResultadoAutorizacion resultado = verificador.verificar(
            VariablesProceso.uuid(ejecucion, "solicitudId"),
            VariablesProceso.uuid(ejecucion, "revisionId")
        );

        if (!resultado.autorizado()) {
            throw new IllegalStateException(
                "No se autoriza la ejecución: "
                    + String.join("; ", resultado.motivos()));
        }
    }
}
