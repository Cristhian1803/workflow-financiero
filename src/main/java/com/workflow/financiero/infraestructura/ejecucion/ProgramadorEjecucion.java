package com.workflow.financiero.infraestructura.ejecucion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el trabajador periódicamente solo cuando la ejecución automática
 * está habilitada. En pruebas se invoca al trabajador de forma manual.
 */
@Component
@ConditionalOnProperty(
    prefix = "ejecucion.trabajador",
    name = "habilitado",
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class ProgramadorEjecucion {

    private final TrabajadorEjecucion trabajador;
    private final TrabajadorRecuperacionEjecucion recuperador;
    private final TrabajadorCompensacion compensador;

    @Scheduled(
        fixedDelayString = "${ejecucion.trabajador.intervalo-ms:2000}"
    )
    public void ejecutar() {
        int procesadas = trabajador.ejecutarPendientes();
        int recuperadas = recuperador.reconciliarPendientes();
        int compensadas = compensador.procesarPendientes();
        if (procesadas > 0 || recuperadas > 0 || compensadas > 0) {
            log.info(
                "Se procesaron {} intenciones, se reconciliaron {} y se "
                    + "compensaron {}",
                procesadas,
                recuperadas,
                compensadas
            );
        }
    }
}
