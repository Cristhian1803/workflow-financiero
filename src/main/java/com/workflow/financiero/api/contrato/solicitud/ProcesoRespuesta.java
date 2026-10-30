package com.workflow.financiero.api.contrato.solicitud;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Estado del proceso asociado a la solicitud. En el Paso 2 todavía no hay
 * instancia de Flowable.
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

    @Schema(example = "false")
    boolean iniciado,

    @Schema(example = "false")
    boolean finalizado,

    @Schema(example = "EVALUANDO_RIESGO")
    EstadoRevision estadoRevision,

    @Schema(description = "Tareas del proceso; vacío en esta etapa")
    List<TareaProcesoRespuesta> tareas
) {

    public ProcesoRespuesta {
        tareas = List.copyOf(tareas);
    }
}
