package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.puerto.PuertoEjecucionFinanciera;
import com.workflow.financiero.dominio.temporal.Instantes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adaptador de demostración para observar la preparación de una ejecución sin
 * llamar a ningún proveedor. No representa una operación financiera real.
 */
@Component
@Slf4j
public class AdaptadorEjecucionControlada implements PuertoEjecucionFinanciera {

    private final List<ObservacionEjecucion> observaciones =
        new CopyOnWriteArrayList<>();

    @Override
    public void preparar(UUID solicitudId, UUID revisionId) {
        registrar(TipoObservacion.PREPARACION, solicitudId, revisionId);
    }

    @Override
    public void registrarResultado(UUID solicitudId, UUID revisionId) {
        registrar(TipoObservacion.RESULTADO, solicitudId, revisionId);
    }

    public List<ObservacionEjecucion> observaciones(UUID revisionId) {
        return observaciones.stream()
            .filter(observacion -> observacion.revisionId().equals(revisionId))
            .toList();
    }

    private void registrar(
        TipoObservacion tipo,
        UUID solicitudId,
        UUID revisionId
    ) {
        observaciones.add(new ObservacionEjecucion(
            tipo,
            solicitudId,
            revisionId,
            Instantes.ahora()
        ));
        log.info(
            "Ejecución controlada [tipo={}, solicitud={}, revisión={}]",
            tipo,
            solicitudId,
            revisionId
        );
    }

    public enum TipoObservacion {
        PREPARACION,
        RESULTADO
    }

    public record ObservacionEjecucion(
        TipoObservacion tipo,
        UUID solicitudId,
        UUID revisionId,
        Instant registradaEn
    ) {
    }
}
