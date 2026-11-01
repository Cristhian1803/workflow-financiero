package com.workflow.financiero.aplicacion.servicio;

import java.util.Locale;

/**
 * Unifica cómo se escriben y buscan los nombres de usuario.
 */
public final class NormalizadorUsuario {

    private NormalizadorUsuario() {
    }

    public static String normalizar(String usuario) {
        return usuario == null ? null : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
