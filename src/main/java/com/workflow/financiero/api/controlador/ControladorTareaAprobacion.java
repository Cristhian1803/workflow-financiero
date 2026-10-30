package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.error.ErrorApiRespuesta;
import com.workflow.financiero.api.contrato.tarea.AprobarTareaPeticion;
import com.workflow.financiero.api.contrato.tarea.DecisionAprobacionRespuesta;
import com.workflow.financiero.api.contrato.tarea.RechazarTareaPeticion;
import com.workflow.financiero.api.contrato.tarea.TareaAprobacionRespuesta;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.aplicacion.servicio.ResultadoDecisionAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioBandejaTareas;
import com.workflow.financiero.aplicacion.servicio.ServicioDecisionAprobacion;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.TareaAprobacionBandeja;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja y decisiones de las tareas de aprobación del actor autenticado.
 */
@RestController
@RequestMapping("/api/v1/tareas-aprobacion")
@RequiredArgsConstructor
@Tag(name = "Tareas de aprobación",
    description = "Consulta y resolución de tareas del aprobador")
public class ControladorTareaAprobacion {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioBandejaTareas servicioBandeja;
    private final ServicioDecisionAprobacion servicioDecision;

    @GetMapping
    @Operation(
        summary = "Listar tareas pendientes",
        description = "Devuelve solo las tareas PENDIENTE de la revisión actual "
            + "visibles para el actor. Incluye las tareas asignadas y las "
            + "tareas sin asignar de sus roles funcionales o delegados. El "
            + "creador de la solicitud no ve sus propias tareas."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tareas visibles"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin rol de aprobación",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    List<TareaAprobacionRespuesta> listar(Principal principal) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());

        return servicioBandeja.listar(actual).stream()
            .map(this::respuesta)
            .toList();
    }

    @PostMapping("/{id}/aprobar")
    @Operation(
        summary = "Aprobar una tarea",
        description = "Registra la aprobación, el evento y el rol funcional, y "
            + "completa la tarea de Flowable en la misma transacción. El "
            + "creador no puede aprobar y un actor no puede aprobar dos roles "
            + "funcionales distintos en la misma solicitud."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tarea aprobada"),
        @ApiResponse(responseCode = "400", description = "Comentario inválido",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Aprobación no permitida",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "Tarea no encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "Tarea ya resuelta o conflicto",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    DecisionAprobacionRespuesta aprobar(
        @Parameter(description = "Identificador de la tarea de negocio")
        @PathVariable UUID id,
        @Valid @RequestBody AprobarTareaPeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        return decision(servicioDecision.aprobar(
            id,
            actual,
            peticion.comentario(),
            Correlacion.obtener(peticionHttp)
        ));
    }

    @PostMapping("/{id}/rechazar")
    @Operation(
        summary = "Rechazar una tarea",
        description = "Registra el rechazo con motivo obligatorio, completa la "
            + "tarea de Flowable y cancela las demás tareas pendientes de la "
            + "revisión en la misma transacción."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tarea rechazada"),
        @ApiResponse(responseCode = "400", description = "Motivo inválido",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Aprobación no permitida",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "Tarea no encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "Tarea ya resuelta o conflicto",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    DecisionAprobacionRespuesta rechazar(
        @Parameter(description = "Identificador de la tarea de negocio")
        @PathVariable UUID id,
        @Valid @RequestBody RechazarTareaPeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        return decision(servicioDecision.rechazar(
            id,
            actual,
            peticion.motivo(),
            Correlacion.obtener(peticionHttp)
        ));
    }

    private TareaAprobacionRespuesta respuesta(TareaAprobacionBandeja tarea) {
        return new TareaAprobacionRespuesta(
            tarea.id(),
            tarea.idSolicitud(),
            tarea.idRevision(),
            tarea.revision(),
            tarea.rol(),
            tarea.estado(),
            tarea.asignadoA(),
            tarea.fechaCreacion(),
            tarea.fechaNotificacion(),
            tarea.fechaVencimiento(),
            tarea.solicitante(),
            tarea.tipoOperacion(),
            tarea.monto(),
            tarea.moneda(),
            tarea.nivelRiesgo(),
            tarea.descripcion(),
            tarea.justificacion()
        );
    }

    private DecisionAprobacionRespuesta decision(
        ResultadoDecisionAprobacion resultado
    ) {
        return new DecisionAprobacionRespuesta(
            resultado.idTarea(),
            resultado.decision(),
            resultado.fecha(),
            resultado.estadoTarea(),
            resultado.estadoRevision(),
            resultado.delegado()
        );
    }
}
