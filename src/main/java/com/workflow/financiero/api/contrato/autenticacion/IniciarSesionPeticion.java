package com.workflow.financiero.api.contrato.autenticacion;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de inicio de sesión.
 */
public record IniciarSesionPeticion(
    @NotBlank(message = "El usuario es obligatorio")
    String usuario,
    @NotBlank(message = "La clave es obligatoria")
    String clave
) {

    @Override
    public String toString() {
        return "IniciarSesionPeticion[usuario=" + usuario + ", clave=***]";
    }
}
