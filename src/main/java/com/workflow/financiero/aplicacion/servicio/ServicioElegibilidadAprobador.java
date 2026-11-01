package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Calcula los roles funcionales que un actor puede atender, ya sea por rol
 * directo o por delegación de un superior configurado.
 */
@Service
@RequiredArgsConstructor
public class ServicioElegibilidadAprobador {

    private static final Set<String> ROLES_FUNCIONALES = Set.of(
        "SUPERVISOR",
        "GERENTE",
        "RIESGOS"
    );

    private final PropiedadesAprobacion propiedadesAprobacion;

    public List<String> rolesVisibles(UsuarioActual actual) {
        Set<String> roles = new TreeSet<>();
        for (String rol : ROLES_FUNCIONALES) {
            if (actual.tieneRol(rol)) {
                roles.add(rol);
            }
        }

        propiedadesAprobacion.superiores().forEach((rolFuncional, rolSuperior) -> {
            if (actual.tieneRol(rolSuperior)) {
                roles.add(rolFuncional);
            }
        });

        return List.copyOf(roles);
    }

    public void verificarPuedeUsarBandeja(UsuarioActual actual) {
        if (rolesVisibles(actual).isEmpty()) {
            throw new AccesoDenegadoException(
                "El usuario no tiene un rol de aprobación o delegación");
        }
    }
}
