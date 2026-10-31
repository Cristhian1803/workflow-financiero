package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.error.ErrorApiRespuesta;
import com.workflow.financiero.api.contrato.notificacion.NotificacionRespuesta;
import com.workflow.financiero.aplicacion.servicio.NotificacionDetalle;
import com.workflow.financiero.aplicacion.servicio.ServicioConsultaNotificaciones;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
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
 * Notificaciones visibles para el usuario autenticado.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Tag(name = "Notificaciones",
    description = "Consulta de notificaciones del usuario autenticado")
public class ControladorNotificacion {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioConsultaNotificaciones servicioConsulta;

    @GetMapping
    @Operation(
        summary = "Listar notificaciones",
        description = "Devuelve las notificaciones dirigidas al actor o a sus "
            + "roles activos, ordenadas por fecha de creación descendente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificaciones visibles"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    List<NotificacionRespuesta> listar(Principal principal) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());

        return servicioConsulta.listar(actual).stream()
            .map(this::respuesta)
            .toList();
    }

    private NotificacionRespuesta respuesta(NotificacionDetalle detalle) {
        return new NotificacionRespuesta(
            detalle.id(),
            detalle.tipo(),
            detalle.contenido(),
            detalle.estado(),
            detalle.idTarea(),
            detalle.idSolicitud(),
            detalle.revision(),
            detalle.fechaCreacion(),
            detalle.fechaLectura()
        );
    }
}
