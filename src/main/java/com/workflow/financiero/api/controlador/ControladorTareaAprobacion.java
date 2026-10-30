package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.error.ErrorApiRespuesta;
import com.workflow.financiero.api.contrato.tarea.TareaAprobacionRespuesta;
import com.workflow.financiero.aplicacion.servicio.ServicioBandejaTareas;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.TareaAprobacionBandeja;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja de tareas de aprobación pendientes del actor autenticado.
 */
@RestController
@RequestMapping("/api/v1/tareas-aprobacion")
@RequiredArgsConstructor
@Tag(name = "Tareas de aprobación",
    description = "Consulta de tareas pendientes del aprobador")
public class ControladorTareaAprobacion {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioBandejaTareas servicioBandeja;

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
}
