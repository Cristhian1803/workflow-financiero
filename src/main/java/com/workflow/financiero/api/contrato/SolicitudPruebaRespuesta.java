package com.workflow.financiero.api.contrato;

import java.util.List;
import java.util.UUID;

public record SolicitudPruebaRespuesta(
    UUID id,
    String estado,
    long monto,
    String moneda,
    String identificadorInstanciaProceso,
    String resultadoSimulado,
    List<EventoSolicitudPruebaRespuesta> historial
) {
}
