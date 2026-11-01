package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.servicio.ContextoCompensacion;
import com.workflow.financiero.aplicacion.servicio.ServicioCompensacion;
import com.workflow.financiero.aplicacion.servicio.ServicioIntencionCompensacion;
import com.workflow.financiero.aplicacion.servicio.TrabajoCompensacion;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Procesa las compensaciones pendientes o que quedaron en curso tras una
 * caída. La llamada al proveedor ocurre fuera de la transacción de reclamo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajadorCompensacion {

    private final ServicioIntencionCompensacion servicioIntencion;
    private final ServicioCompensacion servicioCompensacion;

    private final String identificador = UUID.randomUUID().toString();

    public int procesarPendientes() {
        int procesadas = 0;
        while (true) {
            Optional<TrabajoCompensacion> trabajo =
                servicioIntencion.reclamarSiguiente(identificador);
            if (trabajo.isEmpty()) {
                return procesadas;
            }
            try {
                servicioCompensacion.ejecutar(
                    trabajo.get(), ContextoCompensacion.automatico());
            } catch (RuntimeException error) {
                log.error(
                    "No se pudo completar la compensación {} de la revisión {}: {}",
                    trabajo.get().idCompensacion(),
                    trabajo.get().idRevision(),
                    error.getMessage()
                );
            }
            procesadas++;
        }
    }
}
