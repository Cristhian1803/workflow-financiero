package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.proveedor.OperacionProveedorFinancieroPeticion;
import com.workflow.financiero.api.contrato.proveedor.ResultadoProveedorFinancieroRespuesta;
import com.workflow.financiero.api.error.CodigoError;
import com.workflow.financiero.api.error.ProblemaApi;
import com.workflow.financiero.aplicacion.simulador.ConfiguracionFallosSimulador;
import com.workflow.financiero.aplicacion.simulador.ServicioSimuladorProveedorFinanciero;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulador HTTP del proveedor financiero. Solo existe en desarrollo y
 * pruebas; no mueve dinero ni representa la ejecución de la aplicación.
 */
@RestController
@RequestMapping("/api/v1/integraciones/proveedor-financiero")
@Profile({"desarrollo", "pruebas"})
@RequiredArgsConstructor
@Tag(name = "Proveedor financiero simulado",
    description = "Contrato de demostración con reserva, confirmación, "
        + "consulta y liberación idempotentes")
public class ControladorProveedorFinancieroSimulado {

    private final ServicioSimuladorProveedorFinanciero servicio;
    private final ConfiguracionFallosSimulador fallos;

    @PostMapping("/reservas")
    @Operation(summary = "Reservar disponibilidad para una revisión")
    ResponseEntity<Object> reservar(
        @Valid @RequestBody OperacionProveedorFinancieroPeticion peticion,
        HttpServletRequest peticionHttp
    ) {
        return ejecutar(
            peticion, AccionFinanciera.RESERVA, peticionHttp);
    }

    @PostMapping("/confirmaciones")
    @Operation(summary = "Confirmar una reserva existente")
    ResponseEntity<Object> confirmar(
        @Valid @RequestBody OperacionProveedorFinancieroPeticion peticion,
        HttpServletRequest peticionHttp
    ) {
        return ejecutar(
            peticion, AccionFinanciera.CONFIRMACION, peticionHttp);
    }

    @PostMapping("/liberaciones")
    @Operation(summary = "Liberar una reserva no confirmada")
    ResponseEntity<Object> liberar(
        @Valid @RequestBody OperacionProveedorFinancieroPeticion peticion,
        HttpServletRequest peticionHttp
    ) {
        return ejecutar(
            peticion, AccionFinanciera.LIBERACION, peticionHttp);
    }

    @GetMapping("/operaciones/{clave}")
    @Operation(summary = "Consultar una operación por su clave")
    ResponseEntity<Object> consultar(
        @PathVariable String clave,
        HttpServletRequest peticionHttp
    ) {
        if (fallos.consultaNoDisponible(clave)) {
            return fallo(
                CodigoError.SERVICIO_NO_DISPONIBLE,
                "La consulta simulada no está disponible",
                peticionHttp
            );
        }
        return ResponseEntity.ok(
            ResultadoProveedorFinancieroRespuesta.desde(
                servicio.consultar(clave)));
    }

    private ResponseEntity<Object> ejecutar(
        OperacionProveedorFinancieroPeticion peticion,
        AccionFinanciera accion,
        HttpServletRequest peticionHttp
    ) {
        if (peticion.accion() != accion) {
            throw new DecisionInvalidaException(
                "accion",
                "La acción no corresponde a la ruta solicitada"
            );
        }

        OperacionFinanciera operacion = peticion.aDominio();
        if (fallos.fallaAntesDelEfecto(operacion.clave(), accion)) {
            return fallo(
                CodigoError.ERROR_INTERNO,
                "Fallo simulado antes de aplicar el efecto",
                peticionHttp
            );
        }

        ResultadoOperacionFinanciera resultado = switch (accion) {
            case RESERVA -> servicio.reservar(operacion);
            case CONFIRMACION -> servicio.confirmar(operacion);
            case LIBERACION -> servicio.liberar(operacion);
        };

        if (fallos.pierdeRespuesta(operacion.clave())) {
            return fallo(
                CodigoError.ERROR_INTERNO,
                "La respuesta simulada se perdió después del efecto",
                peticionHttp
            );
        }
        return ResponseEntity.ok(
            ResultadoProveedorFinancieroRespuesta.desde(resultado));
    }

    private ResponseEntity<Object> fallo(
        CodigoError codigo,
        String detalle,
        HttpServletRequest peticion
    ) {
        return ResponseEntity.status(codigo.getEstado())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemaApi.crear(codigo, detalle, peticion));
    }
}
