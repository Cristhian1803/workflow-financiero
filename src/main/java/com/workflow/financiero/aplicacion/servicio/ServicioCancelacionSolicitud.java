package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.aplicacion.excepcion.OperacionNoEncontradaProveedorFinancieroException;
import com.workflow.financiero.aplicacion.puerto.PuertoProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta la cancelación: prepara el estado en una transacción corta,
 * consulta y libera la reserva fuera de transacción y confirma el resultado en
 * otra transacción corta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCancelacionSolicitud {

    private final ServicioIntencionCancelacion servicioIntencion;
    private final PuertoProveedorFinanciero proveedor;

    public ResultadoCancelacionSolicitud cancelar(
        UUID solicitudId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        return cancelarInterno(
            solicitudId, null, actual, motivo, correlacion);
    }

    /**
     * Sobrecarga que conserva la verificación de la revisión esperada para
     * los llamadores anteriores.
     */
    public ResultadoCancelacionSolicitud cancelar(
        UUID solicitudId,
        UUID revisionId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        return cancelarInterno(
            solicitudId, revisionId, actual, motivo, correlacion);
    }

    private ResultadoCancelacionSolicitud cancelarInterno(
        UUID solicitudId,
        UUID revisionEsperadaId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        PreparacionCancelacion preparacion = servicioIntencion.preparar(
            solicitudId,
            revisionEsperadaId,
            actual,
            motivo,
            correlacion
        );

        if (!preparacion.requiereCompensacion()) {
            return preparacion.resultado();
        }

        try {
            ResultadoOperacionFinanciera liberacion = compensar(preparacion);
            return servicioIntencion.confirmarCompensacion(
                preparacion, liberacion);
        } catch (RuntimeException error) {
            log.warn(
                "La compensación de la revisión {} quedó pendiente: {}",
                preparacion.idRevision(),
                error.getMessage()
            );
            try {
                servicioIntencion.marcarCompensacionPendiente(
                    preparacion, error.getMessage());
            } catch (RuntimeException fallo) {
                log.error(
                    "No se pudo registrar la compensación pendiente de la "
                        + "revisión {}: {}",
                    preparacion.idRevision(),
                    fallo.getMessage()
                );
            }
            return new ResultadoCancelacionSolicitud(
                preparacion.idSolicitud(),
                preparacion.idRevision(),
                EstadoRevision.COMPENSANDO
            );
        }
    }

    /**
     * Verifica la reserva antes de liberar. Un 404 significa que no hubo
     * efecto; una reserva confirmada es irreversible y no se libera.
     */
    private ResultadoOperacionFinanciera compensar(
        PreparacionCancelacion preparacion
    ) {
        String claveReserva = ClavesOperacionFinanciera.reserva(
            preparacion.idSolicitud(), preparacion.idRevision());
        ResultadoOperacionFinanciera reserva;
        try {
            reserva = proveedor.consultar(claveReserva);
        } catch (OperacionNoEncontradaProveedorFinancieroException noExiste) {
            return null;
        }

        if (reserva.estado() == EstadoOperacionFinanciera.CONFIRMADA) {
            throw new ConflictoProveedorFinancieroException(
                "La reserva ya fue confirmada; la ejecución es irreversible");
        }
        if (reserva.estado() == EstadoOperacionFinanciera.LIBERADA) {
            return proveedor.consultar(ClavesOperacionFinanciera.liberacion(
                preparacion.idSolicitud(), preparacion.idRevision()));
        }
        if (reserva.estado() != EstadoOperacionFinanciera.RESERVADA) {
            throw new ConflictoProveedorFinancieroException(
                "La reserva no está en un estado compensable");
        }

        OperacionFinanciera liberacion = OperacionFinanciera.crear(
            preparacion.idSolicitud(),
            preparacion.idRevision(),
            AccionFinanciera.LIBERACION,
            preparacion.monto(),
            preparacion.moneda(),
            preparacion.tipoOperacion()
        );
        return proveedor.liberar(liberacion);
    }
}
