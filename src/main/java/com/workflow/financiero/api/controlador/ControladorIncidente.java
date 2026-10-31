package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.error.ErrorApiRespuesta;
import com.workflow.financiero.api.contrato.incidente.IncidenteRespuesta;
import com.workflow.financiero.api.contrato.incidente.ReintentarIncidentePeticion;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.aplicacion.servicio.IncidenteDetalle;
import com.workflow.financiero.aplicacion.servicio.ResultadoReconciliacion;
import com.workflow.financiero.aplicacion.servicio.ResultadoReintentoIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioReintentoIncidente;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta y recuperación controlada de incidentes operativos.
 */
@RestController
@RequestMapping("/api/v1/incidentes")
@RequiredArgsConstructor
@Tag(name = "Incidentes",
    description = "Incidentes abiertos y reintento operativo autorizado")
public class ControladorIncidente {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioIncidente servicioIncidente;
    private final ServicioReintentoIncidente servicioReintento;

    @GetMapping
    @Operation(
        summary = "Listar incidentes abiertos",
        description = "Devuelve los incidentes abiertos o en reintento visibles "
            + "para Operaciones o Administración."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incidentes visibles"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin rol operativo",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    List<IncidenteRespuesta> listar(Principal principal) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        return servicioIncidente.listarAbiertos(actual).stream()
            .map(this::respuesta)
            .toList();
    }

    @PostMapping("/{id}/reintentar")
    @Operation(
        summary = "Reintentar un incidente de ejecución",
        description = "Revalida la autorización, conserva la clave y la huella "
            + "financiera y registra actor, motivo y resultado. Si el resultado "
            + "sigue incierto responde 202; si el incidente no admite reintento, "
            + "responde 409."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incidente resuelto"),
        @ApiResponse(responseCode = "202", description = "Reintento aceptado, resultado aún incierto"),
        @ApiResponse(responseCode = "400", description = "Motivo inválido",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin rol operativo",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "Incidente no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "Reintento no permitido",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    ResponseEntity<IncidenteRespuesta> reintentar(
        @Parameter(description = "Identificador del incidente")
        @PathVariable UUID id,
        @Valid @RequestBody ReintentarIncidentePeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        ResultadoReintentoIncidente resultado = servicioReintento.reintentar(
            id,
            actual,
            peticion.motivo(),
            Correlacion.obtener(peticionHttp)
        );

        HttpStatus estado = switch (resultado.resultado()) {
            case RESUELTO -> HttpStatus.OK;
            case PENDIENTE, FALLIDO -> HttpStatus.ACCEPTED;
            case NO_REINTENTABLE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(estado)
            .body(respuesta(resultado.incidente()));
    }

    private IncidenteRespuesta respuesta(IncidenteDetalle detalle) {
        return new IncidenteRespuesta(
            detalle.id(),
            detalle.idSolicitud(),
            detalle.idRevision(),
            detalle.tipo(),
            detalle.referencia(),
            detalle.estado(),
            detalle.motivo(),
            detalle.actor(),
            detalle.reintentable(),
            detalle.reintentos(),
            detalle.ultimoResultado(),
            detalle.fechaCreacion(),
            detalle.fechaActualizacion()
        );
    }
}
