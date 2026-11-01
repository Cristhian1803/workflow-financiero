package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesEjecucion;
import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorDefinitivoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.ErrorRecuperableProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.OperacionNoEncontradaProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.aplicacion.servicio.ContextoReintento;
import com.workflow.financiero.aplicacion.servicio.ResultadoReconciliacion;
import com.workflow.financiero.aplicacion.servicio.ServicioIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioIntencionEjecucion;
import com.workflow.financiero.aplicacion.servicio.TrabajoEjecucion;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reconoce el estado real del proveedor antes de repetir una operación. Nunca
 * cambia la clave ni la huella y no repite una confirmación a ciegas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajadorRecuperacionEjecucion {

    private static final String MOTIVO_AUTOMATICO =
        "Reconciliación automática del resultado de ejecución";

    private final ServicioIntencionEjecucion servicioIntencion;
    private final ServicioIncidente servicioIncidente;
    private final PuertoProveedorFinanciero proveedor;
    private final PropiedadesEjecucion propiedades;

    private final String identificador = UUID.randomUUID().toString();

    public int reconciliarPendientes() {
        int procesadas = 0;
        while (true) {
            Optional<TrabajoEjecucion> trabajo =
                servicioIntencion.reclamarSiguienteRecuperacion(identificador);
            if (trabajo.isEmpty()) {
                return procesadas;
            }

            TrabajoEjecucion actual = trabajo.get();
            try {
                reconciliarAutomatico(actual);
            } catch (RuntimeException error) {
                log.error(
                    "No se pudo reconciliar la intención {}: {}",
                    actual.intentoId(),
                    error.getMessage()
                );
                programarSiEsPosible(actual, error.getMessage());
            }
            procesadas++;
        }
    }

    public ResultadoReconciliacion reconciliarAutomatico(
        TrabajoEjecucion trabajo
    ) {
        ResultadoReconciliacion resultado = reconciliar(
            trabajo,
            new ContextoReintento(
                null,
                null,
                MOTIVO_AUTOMATICO,
                "recuperacion-ejecucion"
            )
        );
        if (resultado == ResultadoReconciliacion.PENDIENTE) {
            finalizarPendienteAutomatico(trabajo);
        }
        return resultado;
    }

    public ResultadoReconciliacion reconciliarManual(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto
    ) {
        ResultadoReconciliacion resultado = reconciliar(trabajo, contexto);
        if (resultado == ResultadoReconciliacion.PENDIENTE
            || resultado == ResultadoReconciliacion.FALLIDO) {
            servicioIntencion.detenerReintentoAutomatico(
                trabajo, contexto.motivo());
            if (contexto.incidenteId() != null) {
                servicioIncidente.registrarResultado(
                    contexto.incidenteId(),
                    contexto.motivo(),
                    true,
                    contexto.correlacion()
                );
            }
        }
        return resultado;
    }

    private ResultadoReconciliacion reconciliar(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto
    ) {
        try {
            ResultadoOperacionFinanciera reserva = consultarReserva(trabajo);
            validarHuella(trabajo, reserva, AccionFinanciera.RESERVA);
            return reconciliarConReserva(trabajo, contexto, reserva);
        } catch (OperacionNoEncontradaProveedorFinancieroException noExiste) {
            return reintentarDesdeCero(trabajo, contexto, noExiste.getMessage());
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return marcarConflicto(trabajo, contexto, conflicto.getMessage());
        } catch (ErrorRecuperableProveedorFinancieroException recuperable) {
            return marcarPendiente(trabajo, contexto, recuperable.getMessage());
        } catch (ErrorDefinitivoProveedorFinancieroException definitivo) {
            return marcarFalloDefinitivo(
                trabajo, contexto, definitivo.getMessage());
        }
    }

    private ResultadoReconciliacion reconciliarConReserva(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        ResultadoOperacionFinanciera reserva
    ) {
        if (reserva.estado() == EstadoOperacionFinanciera.LIBERADA) {
            return marcarConflicto(
                trabajo,
                contexto,
                "La reserva fue liberada y la ejecución no puede confirmarse"
            );
        }

        if (reserva.estado() == EstadoOperacionFinanciera.CONFIRMADA) {
            try {
                ResultadoOperacionFinanciera confirmacion =
                    consultarConfirmacion(trabajo);
                validarHuella(
                    trabajo, confirmacion, AccionFinanciera.CONFIRMACION);
                return registrarConfirmacion(
                    trabajo, contexto, reserva, confirmacion);
            } catch (OperacionNoEncontradaProveedorFinancieroException error) {
                return marcarConflicto(
                    trabajo,
                    contexto,
                    "La reserva figura confirmada pero no existe la confirmación"
                );
            } catch (ErrorRecuperableProveedorFinancieroException error) {
                return marcarPendiente(trabajo, contexto, error.getMessage());
            } catch (ConflictoProveedorFinancieroException error) {
                return marcarConflicto(trabajo, contexto, error.getMessage());
            }
        }

        try {
            ResultadoOperacionFinanciera confirmacion =
                consultarConfirmacion(trabajo);
            validarHuella(trabajo, confirmacion, AccionFinanciera.CONFIRMACION);
            return registrarConfirmacion(
                trabajo, contexto, reserva, confirmacion);
        } catch (OperacionNoEncontradaProveedorFinancieroException noExiste) {
            if (!puedeReintentar(trabajo, contexto)) {
                return marcarPendiente(
                    trabajo,
                    contexto,
                    "Se agotaron los intentos y no hay confirmación registrada"
                );
            }
            return confirmarReservaExistente(trabajo, contexto, reserva);
        } catch (ErrorRecuperableProveedorFinancieroException recuperable) {
            return marcarPendiente(
                trabajo, contexto, recuperable.getMessage());
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return marcarConflicto(
                trabajo, contexto, conflicto.getMessage());
        } catch (ErrorDefinitivoProveedorFinancieroException definitivo) {
            return marcarFalloDefinitivo(
                trabajo, contexto, definitivo.getMessage());
        }
    }

    private ResultadoReconciliacion reintentarDesdeCero(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        String motivo
    ) {
        if (!puedeReintentar(trabajo, contexto)) {
            return marcarPendiente(trabajo, contexto, motivo);
        }

        try {
            OperacionFinanciera reserva = operacion(
                trabajo, AccionFinanciera.RESERVA);
            ResultadoOperacionFinanciera resultadoReserva =
                proveedor.reservar(reserva);
            OperacionFinanciera confirmacion = operacion(
                trabajo, AccionFinanciera.CONFIRMACION);
            ResultadoOperacionFinanciera resultadoConfirmacion =
                proveedor.confirmar(confirmacion);
            return registrarConfirmacion(
                trabajo, contexto, resultadoReserva, resultadoConfirmacion);
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return marcarConflicto(
                trabajo, contexto, conflicto.getMessage());
        } catch (ErrorRecuperableProveedorFinancieroException recuperable) {
            return marcarPendiente(
                trabajo, contexto, recuperable.getMessage());
        } catch (ErrorDefinitivoProveedorFinancieroException definitivo) {
            return marcarFalloDefinitivo(
                trabajo, contexto, definitivo.getMessage());
        }
    }

    private ResultadoReconciliacion confirmarReservaExistente(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        ResultadoOperacionFinanciera reserva
    ) {
        try {
            OperacionFinanciera confirmacion = operacion(
                trabajo, AccionFinanciera.CONFIRMACION);
            ResultadoOperacionFinanciera resultado =
                proveedor.confirmar(confirmacion);
            return registrarConfirmacion(
                trabajo, contexto, reserva, resultado);
        } catch (ConflictoProveedorFinancieroException conflicto) {
            return marcarConflicto(
                trabajo, contexto, conflicto.getMessage());
        } catch (ErrorRecuperableProveedorFinancieroException recuperable) {
            return marcarPendiente(
                trabajo, contexto, recuperable.getMessage());
        } catch (ErrorDefinitivoProveedorFinancieroException definitivo) {
            return marcarFalloDefinitivo(
                trabajo, contexto, definitivo.getMessage());
        }
    }

    private ResultadoReconciliacion registrarConfirmacion(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        ResultadoOperacionFinanciera reserva,
        ResultadoOperacionFinanciera confirmacion
    ) {
        servicioIntencion.registrarConfirmacion(
            trabajo,
            reserva,
            confirmacion,
            contexto.motivo()
        );
        return ResultadoReconciliacion.RESUELTO;
    }

    private ResultadoReconciliacion marcarPendiente(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        String motivo
    ) {
        servicioIntencion.registrarResultadoDesconocido(trabajo, motivo);
        return ResultadoReconciliacion.PENDIENTE;
    }

    private ResultadoReconciliacion marcarFalloDefinitivo(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        String motivo
    ) {
        servicioIntencion.registrarFalloDefinitivo(trabajo, motivo, true);
        servicioIntencion.cerrarEjecucionDesconocida(trabajo, motivo);
        return ResultadoReconciliacion.FALLIDO;
    }

    private ResultadoReconciliacion marcarConflicto(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto,
        String motivo
    ) {
        servicioIntencion.registrarFalloDefinitivo(trabajo, motivo, false);
        servicioIntencion.cerrarEjecucionDesconocida(trabajo, motivo);
        if (contexto.incidenteId() != null) {
            servicioIncidente.marcarNoReintentable(
                contexto.incidenteId(),
                motivo,
                contexto.correlacion()
            );
            servicioIncidente.registrarResultado(
                contexto.incidenteId(),
                motivo,
                true,
                contexto.correlacion()
            );
        }
        return ResultadoReconciliacion.NO_REINTENTABLE;
    }

    private void finalizarPendienteAutomatico(TrabajoEjecucion trabajo) {
        if (trabajo.intentos() >= propiedades.maxIntentos()) {
            servicioIntencion.detenerReintentoAutomatico(
                trabajo, "Se agotaron los intentos automáticos");
            servicioIntencion.cerrarEjecucionDesconocida(
                trabajo, "Se agotaron los intentos automáticos");
        } else {
            servicioIntencion.programarReintento(
                trabajo, "Nuevo intento de reconciliación programado");
        }
    }

    private void programarSiEsPosible(
        TrabajoEjecucion trabajo,
        String motivo
    ) {
        try {
            if (trabajo.intentos() >= propiedades.maxIntentos()) {
                servicioIntencion.detenerReintentoAutomatico(trabajo, motivo);
            } else {
                servicioIntencion.programarReintento(trabajo, motivo);
            }
        } catch (RuntimeException error) {
            log.error(
                "No se pudo reprogramar la intención {}: {}",
                trabajo.intentoId(),
                error.getMessage()
            );
        }
    }

    private boolean puedeReintentar(
        TrabajoEjecucion trabajo,
        ContextoReintento contexto
    ) {
        return contexto.esManual()
            || trabajo.intentos() < propiedades.maxIntentos();
    }

    private ResultadoOperacionFinanciera consultarReserva(
        TrabajoEjecucion trabajo
    ) {
        return proveedor.consultar(ClavesOperacionFinanciera.reserva(
            trabajo.solicitudId(), trabajo.revisionId()));
    }

    private ResultadoOperacionFinanciera consultarConfirmacion(
        TrabajoEjecucion trabajo
    ) {
        return proveedor.consultar(ClavesOperacionFinanciera.confirmacion(
            trabajo.solicitudId(), trabajo.revisionId()));
    }

    private void validarHuella(
        TrabajoEjecucion trabajo,
        ResultadoOperacionFinanciera resultado,
        AccionFinanciera accion
    ) {
        String esperada = HuellaOperacionFinanciera.calcular(
            accion,
            trabajo.solicitudId(),
            trabajo.revisionId(),
            trabajo.monto(),
            trabajo.moneda(),
            trabajo.tipoOperacion()
        );
        if (!esperada.equals(resultado.huella())) {
            throw new ConflictoProveedorFinancieroException(
                "La huella del proveedor no corresponde a la intención");
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
