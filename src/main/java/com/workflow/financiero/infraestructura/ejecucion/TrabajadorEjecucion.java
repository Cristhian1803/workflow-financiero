package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.aplicacion.servicio.ServicioIntencionEjecucion;
import com.workflow.financiero.aplicacion.servicio.TrabajoEjecucion;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ejecuta las intenciones pendientes. Reclama en una transacción corta, llama
 * al proveedor sin transacción y registra el resultado en otra transacción
 * corta.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajadorEjecucion {

    private final ServicioIntencionEjecucion servicioIntencion;
    private final PuertoProveedorFinanciero proveedor;

    private final String identificador = UUID.randomUUID().toString();

    public int ejecutarPendientes() {
        int procesadas = 0;
        while (true) {
            Optional<TrabajoEjecucion> trabajo =
                servicioIntencion.reclamarSiguiente(identificador);
            if (trabajo.isEmpty()) {
                return procesadas;
            }
            procesar(trabajo.get());
            procesadas++;
        }
    }

    private void procesar(TrabajoEjecucion trabajo) {
        try {
            OperacionFinanciera reserva = operacion(
                trabajo, AccionFinanciera.RESERVA);
            ResultadoOperacionFinanciera resultadoReserva =
                proveedor.reservar(reserva);

            OperacionFinanciera confirmacion = operacion(
                trabajo, AccionFinanciera.CONFIRMACION);
            ResultadoOperacionFinanciera resultadoConfirmacion =
                proveedor.confirmar(confirmacion);

            servicioIntencion.registrarConfirmacion(
                trabajo, resultadoReserva, resultadoConfirmacion);
        } catch (RuntimeException error) {
            // No se reintenta ni se inventa un resultado: I-18b reconcilia.
            log.error(
                "No se pudo completar la intención {} de la revisión {}: {}",
                trabajo.intentoId(),
                trabajo.revisionId(),
                error.getMessage()
            );
        }
    }

    private OperacionFinanciera operacion(
        TrabajoEjecucion trabajo,
        AccionFinanciera accion
    ) {
        return OperacionFinanciera.crear(
            trabajo.solicitudId(),
            trabajo.revisionId(),
            accion,
            trabajo.monto(),
            trabajo.moneda(),
            trabajo.tipoOperacion()
        );
    }
}
