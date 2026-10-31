package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.TipoIncidente;
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
    private final ServicioIntencionCompensacion servicioCompensacion;
    private final ServicioCompensacion compensacion;
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

        return incidente.tipo() == TipoIncidente.COMPENSACION
            ? reintentarCompensacion(
                incidente, incidenteId, actual, motivo, correlacion)
            : reintentarEjecucion(
                incidente, incidenteId, actual, motivo, correlacion);
    }

    private ResultadoReintentoIncidente reintentarEjecucion(
        IncidenteDetalle incidente,
        UUID incidenteId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        TrabajoEjecucion trabajo;
        try {
            trabajo = servicioIntencion.reclamarParaReintentoManual(
                incidente.referencia(),
                "manual:" + actual.usuario()
            );
        } catch (RuntimeException error) {
            return rechazar(
                incidenteId,
                correlacion,
                "No se pudo iniciar el reintento: " + error.getMessage()
            );
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
            return rechazar(
                incidenteId,
                correlacion,
                "El reintento no pudo completarse: " + error.getMessage()
            );
        }

        return new ResultadoReintentoIncidente(
            servicioIncidente.obtener(incidenteId),
            resultado
        );
    }

    private ResultadoReintentoIncidente reintentarCompensacion(
        IncidenteDetalle incidente,
        UUID incidenteId,
        UsuarioActual actual,
        String motivo,
        String correlacion
    ) {
        TrabajoCompensacion trabajo;
        try {
            trabajo = servicioCompensacion.reclamarParaReintentoManual(
                incidente.referencia(),
                "manual:" + actual.usuario()
            );
        } catch (RuntimeException error) {
            return rechazar(
                incidenteId,
                correlacion,
                "No se pudo iniciar la compensación: " + error.getMessage()
            );
        }

        ResultadoCompensacion resultado;
        try {
            resultado = compensacion.ejecutar(
                trabajo,
                ContextoCompensacion.manual(
                    actual,
                    motivo,
                    correlacion,
                    incidenteId
                )
            );
        } catch (RuntimeException error) {
            return rechazar(
                incidenteId,
                correlacion,
                "La compensación no pudo completarse: " + error.getMessage()
            );
        }

        return new ResultadoReintentoIncidente(
            servicioIncidente.obtener(incidenteId),
            aReconciliacion(resultado)
        );
    }

    private ResultadoReintentoIncidente rechazar(
        UUID incidenteId,
        String correlacion,
        String detalle
    ) {
        servicioIncidente.registrarResultado(
            incidenteId, detalle, true, correlacion);
        throw new ReintentoNoPermitidoException(detalle);
    }

    private ResultadoReconciliacion aReconciliacion(
        ResultadoCompensacion resultado
    ) {
        return switch (resultado) {
            case LIBERADA, CANCELADA_SIN_RESERVA, RECHAZADA ->
                ResultadoReconciliacion.RESUELTO;
            case PENDIENTE -> ResultadoReconciliacion.PENDIENTE;
            case FALLIDA, REVISION_MANUAL ->
                ResultadoReconciliacion.FALLIDO;
        };
    }
}
