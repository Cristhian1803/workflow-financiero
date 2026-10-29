package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.CrearSolicitudPruebaPeticion;
import com.workflow.financiero.api.contrato.EventoSolicitudPruebaRespuesta;
import com.workflow.financiero.api.contrato.SolicitudPruebaRespuesta;
import com.workflow.financiero.aplicacion.servicio.ServicioFlujoMinimo;
import com.workflow.financiero.dominio.modelo.EventoSolicitudPrueba;
import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API minima de demostracion.
 */
@RestController
@RequestMapping("/api/v1/solicitudes-prueba")
@Profile({"desarrollo", "pruebas"})
@RequiredArgsConstructor
public class ControladorSolicitudPrueba {

    private final ServicioFlujoMinimo servicioFlujoMinimo;

    @PostMapping
    ResponseEntity<SolicitudPruebaRespuesta> crear(@RequestBody CrearSolicitudPruebaPeticion peticion) {
        UUID id = servicioFlujoMinimo.crearSolicitudYProceso(peticion.monto(), peticion.moneda(), false);
        SolicitudPrueba solicitud = servicioFlujoMinimo.buscar(id).orElseThrow();
        List<EventoSolicitudPrueba> eventos = servicioFlujoMinimo.historial(id);
        return ResponseEntity
            .created(URI.create("/api/v1/solicitudes-prueba/" + id))
            .body(mapear(solicitud, eventos));
    }

    @GetMapping("/{id}")
    ResponseEntity<SolicitudPruebaRespuesta> consultar(@PathVariable UUID id) {
        return servicioFlujoMinimo.buscar(id)
            .map(solicitud -> {
                List<EventoSolicitudPrueba> eventos = servicioFlujoMinimo.historial(id);
                return ResponseEntity.ok(mapear(solicitud, eventos));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private SolicitudPruebaRespuesta mapear(SolicitudPrueba solicitud, List<EventoSolicitudPrueba> eventos) {
        List<EventoSolicitudPruebaRespuesta> historial = eventos.stream()
            .map(evento -> new EventoSolicitudPruebaRespuesta(evento.getId(), evento.getTipo(), evento.getDetalle()))
            .toList();
        return new SolicitudPruebaRespuesta(
            solicitud.getId(),
            solicitud.getEstado(),
            solicitud.getMonto(),
            solicitud.getMoneda(),
            solicitud.getIdentificadorInstanciaProceso(),
            solicitud.getResultadoSimulado(),
            historial
        );
    }
}
