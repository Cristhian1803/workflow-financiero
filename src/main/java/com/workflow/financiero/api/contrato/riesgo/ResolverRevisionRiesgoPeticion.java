package com.workflow.financiero.api.contrato.riesgo;

import com.workflow.financiero.aplicacion.servicio.ResolverRevisionRiesgoComando;
import com.workflow.financiero.dominio.estado.NivelRiesgo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Resolución humana del riesgo cuando la evaluación externa no llegó.
 */
@Schema(description = "Nivel, motivo y evidencia de la revisión manual")
public record ResolverRevisionRiesgoPeticion(
    @NotBlank
    @Pattern(regexp = "BAJO|MEDIO|ALTO")
    @Schema(example = "ALTO")
    String nivelRiesgo,

    @NotBlank @Size(max = 1000)
    @Schema(example = "El proveedor externo no respondió en el plazo")
    String motivo,

    @NotBlank @Size(max = 1000)
    @Schema(example = "Consulta y registro de incidente")
    String evidencia
) {

    public ResolverRevisionRiesgoComando aComando() {
        return new ResolverRevisionRiesgoComando(
            NivelRiesgo.valueOf(nivelRiesgo.trim().toUpperCase(Locale.ROOT)),
            motivo,
            evidencia
        );
    }
}
