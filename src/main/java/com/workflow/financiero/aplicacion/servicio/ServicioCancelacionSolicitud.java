package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.excepcion.CancelacionNoPermitidaException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta la cancelación: prepara el estado en una transacción corta, ejecuta
 * una pasada de compensación fuera de transacción y devuelve el estado seguro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCancelacionSolicitud {

    private final ServicioIntencionCancelacion servicioIntencion;
    private final ServicioCompensacion servicioCompensacion;

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

        ResultadoCompensacion resultado =
            servicioCompensacion.ejecutar(preparacion);
        return switch (resultado) {
            case LIBERADA, CANCELADA_SIN_RESERVA ->
                new ResultadoCancelacionSolicitud(
                    preparacion.idSolicitud(),
                    preparacion.idRevision(),
                    EstadoRevision.CANCELADA
                );
            case RECHAZADA -> throw new CancelacionNoPermitidaException(
                "La ejecución ya fue confirmada; la cancelación fue rechazada");
            case PENDIENTE, FALLIDA, REVISION_MANUAL -> {
                log.info(
                    "La cancelación de la revisión {} quedó pendiente: {}",
                    preparacion.idRevision(),
                    resultado
                );
                yield new ResultadoCancelacionSolicitud(
                    preparacion.idSolicitud(),
                    preparacion.idRevision(),
                    EstadoRevision.COMPENSANDO
                );
            }
        };
    }
}
