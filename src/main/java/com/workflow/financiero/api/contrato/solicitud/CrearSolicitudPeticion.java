package com.workflow.financiero.api.contrato.solicitud;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Datos que el cliente puede enviar al crear una solicitud. La identidad, el
 * estado, el riesgo y los roles no forman parte del contrato.
 */
public record CrearSolicitudPeticion(
    @Schema(description = "Código del catálogo de tipo de operación",
        example = "NORMAL")
    @NotBlank(message = "El tipo de operación es obligatorio")
    String tipoOperacion,

    @Schema(description = "Monto entero en guaraníes", example = "25000000")
    @NotNull(message = "El monto es obligatorio")
    BigDecimal monto,

    @Schema(description = "Código de la moneda; por ahora solo PYG",
        example = "PYG")
    @NotBlank(message = "La moneda es obligatoria")
    String moneda,

    @Schema(description = "Descripción de la operación",
        example = "Pago de demostración")
    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String descripcion,

    @Schema(description = "Justificación de la operación",
        example = "Operación autorizada para pruebas")
    @NotBlank(message = "La justificación es obligatoria")
    @Size(max = 1000, message = "La justificación no puede superar los 1000 caracteres")
    String justificacion
) {
}
