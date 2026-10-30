package com.workflow.financiero.api.contrato.error;

import com.workflow.financiero.api.error.ErrorCampoRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Esquema de documentación del error uniforme. El runtime sigue usando
 * ProblemDetail con estas mismas propiedades.
 */
@Schema(description = "Error uniforme de la API")
public record ErrorApiRespuesta(
    @Schema(example = "about:blank")
    String type,

    @Schema(example = "Datos inválidos")
    String title,

    @Schema(example = "400")
    int status,

    @Schema(example = "La petición contiene datos inválidos")
    String detail,

    @Schema(example = "/api/v1/solicitudes")
    String instance,

    @Schema(example = "DATOS_INVALIDOS")
    String codigo,

    @Schema(example = "corr-manual-123")
    String identificadorCorrelacion,

    @Schema(description = "Errores de campo, cuando corresponda")
    List<ErrorCampoRespuesta> errores
) {
}
