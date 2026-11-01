package com.workflow.financiero.dominio.regla.aprobacion;

import java.util.Set;

/**
 * Regla independiente que aporta roles funcionales al plan de aprobación.
 */
@FunctionalInterface
public interface PoliticaAprobacion {

    Set<String> rolesRequeridos(ContextoAprobacion contexto);
}
