package com.workflow.financiero.dominio.regla.aprobacion;

import com.workflow.financiero.dominio.estado.NivelRiesgo;
import java.util.Objects;
import java.util.Set;

/**
 * Un riesgo alto exige la aprobación del área de Riesgos. Si el monto ya la
 * exige, el coordinador elimina el duplicado.
 */
public class PoliticaAprobacionPorRiesgo implements PoliticaAprobacion {

    @Override
    public Set<String> rolesRequeridos(ContextoAprobacion contexto) {
        Objects.requireNonNull(contexto, "El contexto de aprobación es obligatorio");
        return contexto.nivelRiesgo() == NivelRiesgo.ALTO
            ? Set.of("RIESGOS")
            : Set.of();
    }
}
