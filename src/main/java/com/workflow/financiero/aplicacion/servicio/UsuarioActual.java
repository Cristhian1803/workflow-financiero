package com.workflow.financiero.aplicacion.servicio;

import java.util.List;
import java.util.UUID;

/**
 * Identidad y roles vigentes del usuario autenticado. Los roles se leen de la
 * base, no del token, para que la autorización use el estado actual.
 */
public record UsuarioActual(
    UUID id,
    String usuario,
    String nombre,
    List<String> roles,
    String perfilRiesgo
) {

    public UsuarioActual {
        roles = List.copyOf(roles);
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }
}
