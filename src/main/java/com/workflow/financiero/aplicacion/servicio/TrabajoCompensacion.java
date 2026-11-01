package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoIntentoCompensacion;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos que necesita la compensación para consultar y liberar fuera de
 * transacción. La clave y la huella no cambian entre reintentos.
 */
public record TrabajoCompensacion(
    UUID idCompensacion,
    UUID idSolicitud,
    UUID idRevision,
    UUID idIntentoEjecucion,
    String procesoId,
    String clave,
    String huella,
    BigDecimal monto,
    String moneda,
    String tipoOperacion,
    EstadoIntentoCompensacion estado,
    int intentos,
    boolean despachoEnCurso
) {
}
