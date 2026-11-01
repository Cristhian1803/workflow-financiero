package com.workflow.financiero.api.contrato.riesgo;

import com.workflow.financiero.aplicacion.servicio.NotificacionRiesgoComando;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de la notificación asíncrona del proveedor de riesgo.
 */
@Schema(description = "Resultado de la evaluación externa de una revisión")
public record NotificacionRiesgoPeticion(
    @NotBlank @Size(max = 50)
    @Schema(example = "proveedor-riesgo-simulado")
    String proveedor,

    @NotBlank @Size(max = 100)
    @Schema(example = "evt-123")
    String identificadorEvento,

    @NotNull
    @Schema(example = "6f7f8a8e-5f4a-4d1a-9e0f-1d2c3b4a5e6f")
    UUID identificadorSolicitud,

    @NotNull @Min(1)
    @Schema(example = "1")
    Integer numeroRevision,

    @NotBlank
    @Pattern(regexp = "BAJO|MEDIO|ALTO")
    @Schema(example = "ALTO")
    String nivelRiesgo,

    @NotNull
    @Schema(example = "2026-10-31T12:00:00Z")
    Instant fechaProveedor,

    @Size(max = 1000)
    @Schema(example = "Evaluación externa de demostración")
    String resultado
) {

    public NotificacionRiesgoComando aComando() {
        return new NotificacionRiesgoComando(
            proveedor,
            identificadorEvento,
            identificadorSolicitud,
            numeroRevision,
            nivelRiesgo,
            fechaProveedor,
            resultado
        );
    }
}
