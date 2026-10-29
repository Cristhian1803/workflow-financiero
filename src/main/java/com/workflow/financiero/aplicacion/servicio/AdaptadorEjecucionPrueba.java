package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.modelo.EventoSolicitudPrueba;
import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudPruebaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudPruebaRepositorio;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el paso automatico del flujo minimo sin efectos reales.
 * Solo esta activo en los perfiles de desarrollo y pruebas.
 */
@Component("adaptadorEjecucionPrueba")
@Profile({"desarrollo", "pruebas"})
@RequiredArgsConstructor
public class AdaptadorEjecucionPrueba implements JavaDelegate {

    public static final String RESULTADO_SIMULADO = "SIMULACION_COMPLETADA";

    private final SolicitudPruebaRepositorio solicitudRepositorio;
    private final EventoSolicitudPruebaRepositorio eventoRepositorio;
    private final RuntimeService runtimeService;

    @Override
    public void execute(DelegateExecution execution) {
        ejecutar(execution.getProcessInstanceId());
    }

    void ejecutar(String identificadorInstanciaProceso) {
        String solicitudIdTexto = (String) runtimeService.getVariable(identificadorInstanciaProceso, "solicitudId");
        UUID solicitudId = UUID.fromString(solicitudIdTexto);
        SolicitudPrueba solicitud = solicitudRepositorio.findById(solicitudId)
            .orElseThrow(() -> new IllegalStateException("No existe la solicitud asociada al proceso"));

        solicitud.setEstado(ServicioFlujoMinimo.ESTADO_EJECUTADA_SIMULADA);
        solicitud.setResultadoSimulado(RESULTADO_SIMULADO);
        solicitudRepositorio.save(solicitud);

        var evento = new EventoSolicitudPrueba();
        evento.setId(UUID.randomUUID());
        evento.setSolicitudId(solicitudId);
        evento.setTipo("EJECUCION_SIMULADA");
        evento.setDetalle("Ejecucion simulada completada");
        eventoRepositorio.save(evento);
    }
}
