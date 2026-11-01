package com.workflow.financiero.api.contrato.autenticacion;

import java.util.List;
import java.util.UUID;

/**
 * Datos públicos del usuario autenticado.
 */
public record UsuarioActualRespuesta(
    UUID id,
    String usuario,
    String nombre,
    List<String> roles,
    String perfilRiesgo
) {

    public UsuarioActualRespuesta {
        roles = List.copyOf(roles);
    }
}
