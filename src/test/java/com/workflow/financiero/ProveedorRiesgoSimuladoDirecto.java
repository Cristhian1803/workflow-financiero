package com.workflow.financiero;

import com.workflow.financiero.aplicacion.puerto.PuertoProveedorRiesgo;
import com.workflow.financiero.aplicacion.servicio.SolicitudEvaluacionRiesgo;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Proveedor de riesgo controlable para las pruebas. Captura las solicitudes
 * enviadas y permite simular un fallo de envío.
 */
@Component
@Profile("pruebas")
@Primary
public class ProveedorRiesgoSimuladoDirecto implements PuertoProveedorRiesgo {

    private final List<SolicitudEvaluacionRiesgo> solicitudes =
        new CopyOnWriteArrayList<>();
    private final AtomicBoolean fallar = new AtomicBoolean(false);

    @Override
    public String solicitarEvaluacion(SolicitudEvaluacionRiesgo solicitud) {
        if (fallar.get()) {
            throw new IllegalStateException(
                "El proveedor de riesgo simulado no está disponible");
        }
        solicitudes.add(solicitud);
        return "RIESGO-" + solicitud.correlacion();
    }

    public void reiniciar() {
        solicitudes.clear();
        fallar.set(false);
    }

    public void fallarProxima() {
        fallar.set(true);
    }

    public void dejarDeFallar() {
        fallar.set(false);
    }

    public List<SolicitudEvaluacionRiesgo> solicitudes() {
        return List.copyOf(solicitudes);
    }

    public SolicitudEvaluacionRiesgo ultimaSolicitud() {
        return solicitudes.isEmpty()
            ? null
            : solicitudes.get(solicitudes.size() - 1);
    }
}
