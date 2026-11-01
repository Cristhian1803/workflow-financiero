package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.riesgo.ResolucionRevisionRiesgoRespuesta;
import com.workflow.financiero.api.contrato.riesgo.ResolverRevisionRiesgoPeticion;
import com.workflow.financiero.api.contrato.riesgo.TareaRevisionRiesgoRespuesta;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.aplicacion.servicio.ResultadoResolucionRevisionRiesgo;
import com.workflow.financiero.aplicacion.servicio.ServicioTareaRevisionRiesgo;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.TareaRevisionRiesgoBandeja;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Bandeja y resolución de las tareas manuales de riesgo.
 */
@RestController
@RequestMapping("/api/v1/tareas-revision-riesgo")
@RequiredArgsConstructor
@Tag(name = "Revisión manual de riesgo",
    description = "Tareas humanas cuando la evaluación externa no llega")
public class ControladorTareaRevisionRiesgo {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioTareaRevisionRiesgo servicioRevision;

    @GetMapping
    @Operation(
        summary = "Listar tareas de revisión manual",
        description = "Devuelve solo las tareas PENDIENTE de la revisión actual "
            + "visibles para un analista o jefe de Riesgos. El creador de la "
            + "solicitud no ve su propia tarea."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tareas visibles"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin rol autorizado")
    })
    List<TareaRevisionRiesgoRespuesta> listar(Principal principal) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        return servicioRevision.listar(actual).stream()
            .map(this::respuesta)
            .toList();
    }

    @PostMapping("/{id}/resolver")
    @Operation(
        summary = "Resolver una tarea de revisión manual",
        description = "Registra nivel, motivo y evidencia, consolida el máximo "
            + "con el riesgo interno y recalcula el plan. No crea decisiones "
            + "de aprobación ni sustituye ninguna aprobación."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Revisión resuelta"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso"),
        @ApiResponse(responseCode = "404", description = "Tarea no encontrada"),
        @ApiResponse(responseCode = "409", description = "Tarea ya resuelta")
    })
    ResolucionRevisionRiesgoRespuesta resolver(
        @Parameter(description = "Identificador de la tarea de revisión")
        @PathVariable UUID id,
        @Valid @RequestBody ResolverRevisionRiesgoPeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        return respuesta(servicioRevision.resolver(
            id,
            actual,
            peticion.aComando(),
            Correlacion.obtener(peticionHttp)
        ));
    }

    private TareaRevisionRiesgoRespuesta respuesta(
        TareaRevisionRiesgoBandeja tarea
    ) {
        return new TareaRevisionRiesgoRespuesta(
            tarea.id(),
            tarea.idSolicitud(),
            tarea.idRevision(),
            tarea.revision(),
            tarea.estado(),
            tarea.solicitante(),
            tarea.tipoOperacion(),
            tarea.monto(),
            tarea.moneda(),
            tarea.nivelRiesgo(),
            tarea.descripcion(),
            tarea.justificacion(),
            tarea.fechaCreacion()
        );
    }

    private ResolucionRevisionRiesgoRespuesta respuesta(
        ResultadoResolucionRevisionRiesgo resultado
    ) {
        return new ResolucionRevisionRiesgoRespuesta(
            resultado.idTarea(),
            resultado.estadoTarea(),
            resultado.estadoRevision(),
            resultado.nivelRiesgo(),
            resultado.fecha()
        );
    }
}
