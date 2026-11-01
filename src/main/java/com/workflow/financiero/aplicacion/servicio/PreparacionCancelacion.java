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
    boolean intencionEnCompensacion,
    UUID idCompensacion,
    boolean despachoEnCurso,
    int intentos
) {

    public ResultadoCancelacionSolicitud resultado() {
        return new ResultadoCancelacionSolicitud(
            idSolicitud,
            idRevision,
            estado
        );
    }

    public static PreparacionCancelacion desde(
        TrabajoCompensacion trabajo,
        ContextoCompensacion contexto
    ) {
        return new PreparacionCancelacion(
            trabajo.idSolicitud(),
            trabajo.idRevision(),
            trabajo.idIntentoEjecucion(),
            trabajo.monto(),
            trabajo.moneda(),
            trabajo.tipoOperacion(),
            contexto.motivo(),
            contexto.correlacion(),
            contexto.actual(),
            EstadoRevision.COMPENSANDO,
            true,
            true,
            trabajo.idCompensacion(),
            trabajo.despachoEnCurso(),
            trabajo.intentos()
        );
    }
}
