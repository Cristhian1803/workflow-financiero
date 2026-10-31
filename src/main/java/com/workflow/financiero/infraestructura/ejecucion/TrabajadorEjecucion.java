package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
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
    private final TrabajadorRecuperacionEjecucion recuperador;
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
            if (!servicioIntencion.puedeDespachar(trabajo)) {
                servicioIntencion.abortarDespacho(
                    trabajo,
                    "Cancelación solicitada antes del despacho"
                );
                return;
            }

            OperacionFinanciera reserva = operacion(
                trabajo, AccionFinanciera.RESERVA);
            ResultadoOperacionFinanciera resultadoReserva =
                proveedor.reservar(reserva);

            if (!servicioIntencion.puedeConfirmar(trabajo)) {
                servicioIntencion.abortarDespacho(
                    trabajo,
                    "Cancelación solicitada antes de confirmar"
                );
                return;
            }

            OperacionFinanciera confirmacion = operacion(
                trabajo, AccionFinanciera.CONFIRMACION);
            ResultadoOperacionFinanciera resultadoConfirmacion =
                proveedor.confirmar(confirmacion);

            servicioIntencion.registrarConfirmacion(
                trabajo, resultadoReserva, resultadoConfirmacion);
        } catch (ConflictoProveedorFinancieroException error) {
            registrarConflicto(trabajo, error);
        } catch (ErrorDefinitivoProveedorFinancieroException error) {
            registrarFalloDefinitivo(trabajo, error);
        } catch (ErrorRecuperableProveedorFinancieroException error) {
            registrarYReconciliar(trabajo, error);
        } catch (RuntimeException error) {
            log.error(
                "No se pudo completar la intención {} de la revisión {}: {}",
                trabajo.intentoId(),
                trabajo.revisionId(),
                error.getMessage()
            );
            registrarYReconciliar(trabajo, error);
        }
    }

    private void registrarConflicto(
        TrabajoEjecucion trabajo,
        RuntimeException error
    ) {
        try {
            servicioIntencion.registrarFalloDefinitivo(
                trabajo, error.getMessage(), false);
            servicioIntencion.cerrarEjecucionDesconocida(
                trabajo, error.getMessage());
        } catch (RuntimeException fallo) {
            log.error(
                "No se pudo registrar el conflicto de la intención {}: {}",
                trabajo.intentoId(),
                fallo.getMessage()
            );
        }
    }

    private void registrarFalloDefinitivo(
        TrabajoEjecucion trabajo,
        RuntimeException error
    ) {
        try {
            servicioIntencion.registrarFalloDefinitivo(
                trabajo, error.getMessage(), true);
            servicioIntencion.cerrarEjecucionDesconocida(
                trabajo, error.getMessage());
        } catch (RuntimeException fallo) {
            log.error(
                "No se pudo registrar el fallo de la intención {}: {}",
                trabajo.intentoId(),
                fallo.getMessage()
            );
        }
    }

    private void registrarYReconciliar(
        TrabajoEjecucion trabajo,
        RuntimeException error
    ) {
        try {
            servicioIntencion.registrarResultadoDesconocido(
                trabajo, error.getMessage());
            recuperador.reconciliarAutomatico(trabajo);
        } catch (RuntimeException fallo) {
            log.error(
                "No se pudo recuperar la intención {}: {}",
                trabajo.intentoId(),
                fallo.getMessage()
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
