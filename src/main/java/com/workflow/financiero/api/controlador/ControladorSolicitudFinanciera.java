package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.error.ErrorApiRespuesta;
import com.workflow.financiero.api.contrato.solicitud.CrearSolicitudPeticion;
import com.workflow.financiero.api.contrato.solicitud.EventoHistorialRespuesta;
import com.workflow.financiero.api.contrato.solicitud.PaginaRespuesta;
import com.workflow.financiero.api.contrato.solicitud.ProcesoRespuesta;
import com.workflow.financiero.api.contrato.solicitud.SolicitudResumenRespuesta;
import com.workflow.financiero.api.contrato.solicitud.SolicitudRespuesta;
import com.workflow.financiero.api.contrato.solicitud.TareaProcesoRespuesta;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.aplicacion.servicio.CrearSolicitudComando;
import com.workflow.financiero.aplicacion.servicio.EstadoProceso;
import com.workflow.financiero.aplicacion.servicio.EventoHistorial;
import com.workflow.financiero.aplicacion.servicio.PaginaSolicitudes;
import com.workflow.financiero.aplicacion.servicio.ServicioConsultaSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioReenvioSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioRegistroSolicitud;
import com.workflow.financiero.aplicacion.servicio.ServicioUsuarioActual;
import com.workflow.financiero.aplicacion.servicio.SolicitudDetalle;
import com.workflow.financiero.aplicacion.servicio.SolicitudResumen;
import com.workflow.financiero.aplicacion.servicio.TareaProceso;
import com.workflow.financiero.aplicacion.servicio.UsuarioActual;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import com.workflow.financiero.dominio.excepcion.SolicitudInvalidaException;
import com.workflow.financiero.dominio.regla.validacion.ErrorCampo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de solicitudes. La creación inicia el proceso BPMN y deja la revisión en
 * APROBADA o PENDIENTE_APROBACION según el plan calculado.
 */
@RestController
@RequestMapping("/api/v1/solicitudes")
@RequiredArgsConstructor
@Tag(name = "Solicitudes", description = "Creación y consulta de solicitudes")
public class ControladorSolicitudFinanciera {

    private final ServicioUsuarioActual servicioUsuarioActual;
    private final ServicioRegistroSolicitud servicioRegistro;
    private final ServicioReenvioSolicitud servicioReenvio;
    private final ServicioConsultaSolicitud servicioConsulta;

    @PostMapping
    @Operation(
        summary = "Crear una solicitud",
        description = "Valida, clasifica el riesgo, calcula el plan e inicia el "
            + "proceso. La revisión queda en APROBADA si no requiere "
            + "aprobaciones o en PENDIENTE_APROBACION si las requiere."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Solicitud creada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin rol SOLICITANTE",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "Conflicto de estado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    ResponseEntity<SolicitudRespuesta> crear(
        @Valid @RequestBody CrearSolicitudPeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        String correlacion = Correlacion.obtener(peticionHttp);

        SolicitudDetalle detalle = servicioRegistro.registrar(
            actual,
            new CrearSolicitudComando(
                peticion.tipoOperacion(),
                peticion.monto(),
                peticion.moneda(),
                peticion.descripcion(),
                peticion.justificacion()
            ),
            correlacion
        );

        return ResponseEntity
            .created(URI.create("/api/v1/solicitudes/" + detalle.id()))
            .body(respuesta(detalle));
    }

    @PostMapping("/{id}/reenvios")
    @Operation(
        summary = "Reenviar una solicitud rechazada",
        description = "Solo el dueño puede reenviar desde la revisión actual "
            + "RECHAZADA, dentro de 30 días y con un máximo de tres revisiones. "
            + "Crea una revisión y un proceso nuevos, recalcula riesgo y plan y "
            + "conserva el historial anterior."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Solicitud reenviada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "El actor no es el dueño",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "No encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "La revisión no está rechazada",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "422", description = "Reenvío no permitido",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    ResponseEntity<SolicitudRespuesta> reenviar(
        @Parameter(description = "Identificador de la solicitud")
        @PathVariable UUID id,
        @Valid @RequestBody CrearSolicitudPeticion peticion,
        Principal principal,
        HttpServletRequest peticionHttp
    ) {
        UsuarioActual actual = servicioUsuarioActual.obtenerActual(
            principal.getName());
        String correlacion = Correlacion.obtener(peticionHttp);

        SolicitudDetalle detalle = servicioReenvio.reenviar(
            id,
            actual,
            new CrearSolicitudComando(
                peticion.tipoOperacion(),
                peticion.monto(),
                peticion.moneda(),
                peticion.descripcion(),
                peticion.justificacion()
            ),
            correlacion
        );

        return ResponseEntity
            .created(URI.create("/api/v1/solicitudes/" + detalle.id()))
            .body(respuesta(detalle));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una solicitud",
        description = "El dueño o un administrador pueden verla. "
            + "Una solicitud ajena no visible responde 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitud encontrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin permiso",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "No encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    SolicitudRespuesta obtener(
        @Parameter(description = "Identificador de la solicitud")
        @PathVariable UUID id,
        Principal principal
    ) {
        return respuesta(servicioConsulta.obtener(id, actual(principal)));
    }

    @GetMapping
    @Operation(summary = "Listar solicitudes",
        description = "El administrador ve todas; el solicitante ve solo las "
            + "propias. El orden es por fecha de creación descendente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de solicitudes"),
        @ApiResponse(responseCode = "400", description = "Paginación inválida",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin permiso",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    PaginaRespuesta<SolicitudResumenRespuesta> listar(
        @Parameter(description = "Página, desde cero", example = "0")
        @RequestParam(defaultValue = "0") int pagina,
        @Parameter(description = "Tamaño entre 1 y 100", example = "20")
        @RequestParam(defaultValue = "20") int tamano,
        @Parameter(description = "Filtro opcional por estado de la revisión")
        @RequestParam(required = false) EstadoRevision estado,
        Principal principal
    ) {
        validarPaginacion(pagina, tamano);
        PaginaSolicitudes<SolicitudResumen> resultado = servicioConsulta.listar(
            pagina, tamano, estado, actual(principal));

        return new PaginaRespuesta<>(
            resultado.contenido().stream().map(this::resumen).toList(),
            resultado.pagina(),
            resultado.tamano(),
            resultado.totalElementos(),
            resultado.totalPaginas()
        );
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Consultar el historial",
        description = "Devuelve los eventos ordenados. Puede verlos el dueño, "
            + "un auditor o un administrador.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial de la solicitud"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin permiso",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "No encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    List<EventoHistorialRespuesta> historial(
        @Parameter(description = "Identificador de la solicitud")
        @PathVariable UUID id,
        Principal principal
    ) {
        return servicioConsulta.historial(id, actual(principal)).stream()
            .map(this::evento)
            .toList();
    }

    @GetMapping("/{id}/proceso")
    @Operation(summary = "Consultar el estado del proceso",
        description = "Consulta la instancia activa o finalizada del motor. "
            + "Incluye las actividades y tareas activas visibles.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado del proceso"),
        @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Sin permiso",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "404", description = "No encontrada o no visible",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class))),
        @ApiResponse(responseCode = "500", description = "Error inesperado",
            content = @Content(schema = @Schema(implementation = ErrorApiRespuesta.class)))
    })
    ProcesoRespuesta proceso(
        @Parameter(description = "Identificador de la solicitud")
        @PathVariable UUID id,
        Principal principal
    ) {
        return procesoRespuesta(servicioConsulta.proceso(id, actual(principal)));
    }

    private UsuarioActual actual(Principal principal) {
        return servicioUsuarioActual.obtenerActual(principal.getName());
    }

    private void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0) {
            throw new SolicitudInvalidaException(List.of(new ErrorCampo(
                "pagina", "La página debe ser mayor o igual a 0")));
        }
        if (tamano < 1 || tamano > 100) {
            throw new SolicitudInvalidaException(List.of(new ErrorCampo(
                "tamano", "El tamaño debe estar entre 1 y 100")));
        }
    }

    private SolicitudRespuesta respuesta(SolicitudDetalle detalle) {
        return new SolicitudRespuesta(
            detalle.id(),
            detalle.idRevision(),
            detalle.revision(),
            detalle.solicitante(),
            detalle.tipoOperacion(),
            detalle.monto(),
            detalle.moneda(),
            detalle.descripcion(),
            detalle.justificacion(),
            detalle.estado(),
            detalle.nivelRiesgo(),
            detalle.aprobacionesRequeridas(),
            detalle.requiereEvaluacionExterna(),
            detalle.fechaCreacion(),
            detalle.fechaActualizacion()
        );
    }

    private SolicitudResumenRespuesta resumen(SolicitudResumen resumen) {
        return new SolicitudResumenRespuesta(
            resumen.id(),
            resumen.revision(),
            resumen.solicitante(),
            resumen.tipoOperacion(),
            resumen.monto(),
            resumen.moneda(),
            resumen.estado(),
            resumen.nivelRiesgo(),
            resumen.fechaCreacion()
        );
    }

    private EventoHistorialRespuesta evento(EventoHistorial evento) {
        return new EventoHistorialRespuesta(
            evento.id(),
            evento.revision(),
            evento.tipo(),
            evento.actor(),
            evento.rol(),
            evento.estadoAnterior(),
            evento.estadoNuevo(),
            evento.detalle(),
            evento.identificadorCorrelacion(),
            evento.fecha()
        );
    }

    private ProcesoRespuesta procesoRespuesta(EstadoProceso proceso) {
        List<TareaProcesoRespuesta> tareas = proceso.tareas().stream()
            .map(this::tarea)
            .toList();

        return new ProcesoRespuesta(
            proceso.idSolicitud(),
            proceso.revision(),
            proceso.identificadorProceso(),
            proceso.iniciado(),
            proceso.finalizado(),
            proceso.estadoRevision(),
            proceso.actividadesActivas(),
            tareas
        );
    }

    private TareaProcesoRespuesta tarea(TareaProceso tarea) {
        return new TareaProcesoRespuesta(
            tarea.id(),
            tarea.rol(),
            tarea.nombre(),
            tarea.estado(),
            tarea.asignadoA(),
            tarea.fechaVencimiento()
        );
    }
}
