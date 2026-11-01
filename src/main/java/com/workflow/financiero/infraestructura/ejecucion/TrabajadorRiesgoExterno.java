package com.workflow.financiero.infraestructura.ejecucion;

import com.workflow.financiero.aplicacion.puerto.PuertoProveedorRiesgo;
import com.workflow.financiero.aplicacion.servicio.ServicioEventoExterno;
import com.workflow.financiero.aplicacion.servicio.ServicioSolicitudRiesgoExterno;
import com.workflow.financiero.aplicacion.servicio.SolicitudEvaluacionRiesgo;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reintenta los envíos de evaluación y la correlación de eventos pendientes.
 * La llamada al proveedor ocurre fuera de la transacción de reclamo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajadorRiesgoExterno {

    private final ServicioSolicitudRiesgoExterno servicioSolicitud;
    private final ServicioEventoExterno servicioEvento;
    private final PuertoProveedorRiesgo proveedor;

    private final String identificador = UUID.randomUUID().toString();

    public int procesarPendientes() {
        int procesadas = enviarSolicitudes();
        int correlacionadas = correlacionarEventos();
        return procesadas + correlacionadas;
    }

    private int enviarSolicitudes() {
        int procesadas = 0;
        Set<UUID> visitadas = new HashSet<>();
        while (true) {
            Optional<SolicitudEvaluacionRiesgo> trabajo =
                servicioSolicitud.reclamarSiguiente(identificador);
            if (trabajo.isEmpty()) {
                return procesadas;
            }
            SolicitudEvaluacionRiesgo solicitud = trabajo.get();
            if (!visitadas.add(solicitud.idEvaluacion())) {
                return procesadas;
            }
            try {
                String referencia = proveedor.solicitarEvaluacion(solicitud);
                servicioSolicitud.registrarEnvio(
                    solicitud.idEvaluacion(), referencia);
            } catch (RuntimeException error) {
                log.error(
                    "No se pudo enviar la evaluación externa {}: {}",
                    solicitud.correlacion(),
                    error.getMessage()
                );
                servicioSolicitud.registrarFallo(
                    solicitud.idEvaluacion(), error.getMessage());
                // Se detiene la pasada para no reclamar la misma solicitud
                // fallida en el mismo ciclo; el siguiente ciclo la reintenta.
                return procesadas + 1;
            }
            procesadas++;
        }
    }

    private int correlacionarEventos() {
        int procesadas = 0;
        Set<UUID> visitados = new HashSet<>();
        while (true) {
            Optional<UUID> evento =
                servicioEvento.reclamarSiguienteParaCorrelacion();
            if (evento.isEmpty()) {
                return procesadas;
            }
            UUID idEvento = evento.get();
            if (!visitados.add(idEvento)) {
                return procesadas;
            }
            try {
                servicioEvento.correlacionar(idEvento);
            } catch (RuntimeException error) {
                log.error(
                    "No se pudo correlacionar el evento externo {}: {}",
                    idEvento,
                    error.getMessage()
                );
                servicioEvento.registrarFalloCorrelacion(
                    idEvento, error.getMessage());
            }
            procesadas++;
        }
    }
}
