package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Estado del proceso asociado a la revisión actual.
 */
@Schema(description = "Estado del proceso de la revisión actual")
public record ProcesoRespuesta(
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID idSolicitud,

    @Schema(example = "1")
    int revision,

    @Schema(description = "Nulo mientras el proceso no se haya iniciado",
        nullable = true)
    String identificadorProceso,

    @Schema(example = "true")
    boolean iniciado,

    @Schema(example = "false")
    boolean finalizado,

    @Schema(example = "PENDIENTE_APROBACION")
    EstadoRevision estadoRevision,

    @Schema(description = "Actividades activas informadas por el motor",
        example = "[\"tareaSupervisor\"]")
    List<String> actividadesActivas,

    @Schema(description = "Tareas activas del proceso")
    List<TareaProcesoRespuesta> tareas
) {

    public ProcesoRespuesta {
        actividadesActivas = List.copyOf(actividadesActivas);
        tareas = List.copyOf(tareas);
    }
}
