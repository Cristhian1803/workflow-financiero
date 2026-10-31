package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos que necesita la cancelación para decidir y, si corresponde, liberar
 * la reserva fuera de la transacción.
 */
public record PreparacionCancelacion(
    UUID idSolicitud,
    UUID idRevision,
    UUID idIntento,
    BigDecimal monto,
    String moneda,
    String tipoOperacion,
    String motivo,
    String correlacion,
    UsuarioActual actual,
    EstadoRevision estado,
    boolean requiereCompensacion,
    boolean intencionEnCompensacion
) {

    public ResultadoCancelacionSolicitud resultado() {
        return new ResultadoCancelacionSolicitud(
            idSolicitud,
            idRevision,
            estado
        );
    }
}
