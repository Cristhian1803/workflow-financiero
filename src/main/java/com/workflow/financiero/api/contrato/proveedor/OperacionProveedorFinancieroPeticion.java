package com.workflow.financiero.api.contrato.proveedor;

import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Petición común de reserva, confirmación y liberación del simulador.
 */
public record OperacionProveedorFinancieroPeticion(
    @NotNull UUID solicitudId,
    @NotNull UUID revisionId,
    @NotNull AccionFinanciera accion,
    @NotBlank @Size(max = 100) String clave,
    @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}") String huella,
    @NotNull @Positive BigDecimal monto,
    @NotBlank @Size(min = 3, max = 3) String moneda,
    @NotBlank @Size(max = 30) String tipoOperacion
) {

    public OperacionFinanciera aDominio() {
        return new OperacionFinanciera(
            solicitudId,
            revisionId,
            accion,
            clave,
            huella,
            monto,
            moneda,
            tipoOperacion
        );
    }
}
