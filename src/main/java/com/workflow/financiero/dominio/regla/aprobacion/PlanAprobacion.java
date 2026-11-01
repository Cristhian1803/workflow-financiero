package com.workflow.financiero.dominio.regla.aprobacion;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Roles funcionales que deben aprobar una revisión. Es inmutable y no admite
 * duplicados.
 */
public record PlanAprobacion(Set<String> roles) {

    public PlanAprobacion {
        if (roles == null) {
            throw new IllegalArgumentException("Los roles del plan son obligatorios");
        }

        Set<String> unicos = new LinkedHashSet<>();
        for (String rol : roles) {
            if (rol == null || rol.isBlank()) {
                throw new IllegalArgumentException("El código de rol no puede estar vacío");
            }
            unicos.add(rol);
        }
        roles = Collections.unmodifiableSet(unicos);
    }

    public boolean estaVacio() {
        return roles.isEmpty();
    }

    public boolean requiere(String codigo) {
        return roles.contains(codigo);
    }

    public int cantidad() {
        return roles.size();
    }
}
