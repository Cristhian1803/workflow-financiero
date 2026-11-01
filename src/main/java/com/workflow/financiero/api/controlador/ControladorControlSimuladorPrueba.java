package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.proveedor.ConfigurarFalloSimuladorPeticion;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control de fallos del simulador. Existe solo en pruebas para no abrir una
 * ruta de configuración peligrosa en otros perfiles.
 */
@RestController
@RequestMapping("/api/v1/integraciones/proveedor-financiero/pruebas")
@Profile("pruebas")
@Hidden
@RequiredArgsConstructor
public class ControladorControlSimuladorPrueba {

    private final ConfiguracionFallosSimulador fallos;

    @PostMapping("/fallos")
    ResponseEntity<Void> configurar(
        @Valid @RequestBody ConfigurarFalloSimuladorPeticion peticion
    ) {
        fallos.configurar(peticion.clave(), peticion.modo());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fallos")
    ResponseEntity<Void> limpiar() {
        fallos.limpiar();
        return ResponseEntity.noContent().build();
    }
}
