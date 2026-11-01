package com.workflow.financiero.api.contrato.proveedor;

import com.workflow.financiero.aplicacion.simulador.ModoFalloSimulador;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de un fallo controlado para una clave de operación.
 */
public record ConfigurarFalloSimuladorPeticion(
    @NotBlank String clave,
    @NotNull ModoFalloSimulador modo
) {
}
