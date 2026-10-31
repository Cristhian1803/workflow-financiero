package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.ReintentoNoPermitidoException;
import com.workflow.financiero.infraestructura.ejecucion.TrabajadorRecuperacionEjecucion;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orquesta el reintento manual: prepara la intención en una transacción corta,
 * llama al proveedor fuera de transacción y registra el resultado en otra.
 */
@Service
@RequiredArgsConstructor
public class ServicioReintentoIncidente {

    private final ServicioIncidente servicioIncidente;
    private final ServicioIntencionEjecucion servicioIntencion;
    private final TrabajadorRecuperacionEjecucion recuperador;

    public ResultadoReintentoIncidente reintentar(
        UUID incidenteId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        IncidenteDetalle incidente = servicioIncidente.obtenerParaReintento(
            incidenteId, actual);
        servicioIncidente.marcarReintentando(
            incidenteId, actual, motivo, correlacion);

        TrabajoEjecucion trabajo;
        try {
            trabajo = servicioIntencion.reclamarParaReintentoManual(
                incidente.referencia(),
                "manual:" + actual.usuario()
            );
        } catch (RuntimeException error) {
            servicioIncidente.registrarResultado(
                incidenteId,
                "No se pudo iniciar el reintento: " + error.getMessage(),
                true,
                correlacion
            );
            throw new ReintentoNoPermitidoException(
                "No se pudo iniciar el reintento: " + error.getMessage());
        }

        ResultadoReconciliacion resultado;
        try {
            resultado = recuperador.reconciliarManual(
                trabajo,
                new ContextoReintento(
                    incidenteId,
                    actual,
                    motivo,
                    correlacion
                )
            );
        } catch (RuntimeException error) {
            servicioIncidente.registrarResultado(
                incidenteId,
                "El reintento no pudo completarse: " + error.getMessage(),
                true,
                correlacion
            );
            throw new ReintentoNoPermitidoException(
                "El reintento no pudo completarse: " + error.getMessage());
        }

        return new ResultadoReintentoIncidente(
            servicioIncidente.obtener(incidenteId),
            resultado
        );
    }
}
