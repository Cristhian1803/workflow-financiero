package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.PoliticaAprobacionInvalidaException;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.regla.aprobacion.ContextoAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PlanAprobacion;
import com.workflow.financiero.dominio.regla.aprobacion.PoliticaAprobacion;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Coordina las políticas de aprobación. Une sus resultados, elimina
 * duplicados y comprueba que todos los roles sean aprobadores activos del
 * catálogo.
 */
@Component
public class CalculadoraPlanAprobacion {

    private final List<PoliticaAprobacion> politicas;
    private final RolRepositorio rolRepositorio;

    public CalculadoraPlanAprobacion(
        List<PoliticaAprobacion> politicas,
        RolRepositorio rolRepositorio
    ) {
        if (politicas == null || politicas.isEmpty()) {
            throw new IllegalArgumentException(
                "Debe existir al menos una política de aprobación");
        }
        if (rolRepositorio == null) {
            throw new IllegalArgumentException("El repositorio de roles es obligatorio");
        }

        this.politicas = List.copyOf(politicas);
        this.rolRepositorio = rolRepositorio;
    }

    public PlanAprobacion calcular(ContextoAprobacion contexto) {
        Objects.requireNonNull(contexto, "El contexto de aprobación es obligatorio");

        Set<String> codigos = new LinkedHashSet<>();
        for (PoliticaAprobacion politica : politicas) {
            Set<String> roles = politica.rolesRequeridos(contexto);
            if (roles == null) {
                throw new PoliticaAprobacionInvalidaException(
                    "La política " + politica.getClass().getSimpleName()
                        + " no devolvió ningún conjunto de roles");
            }
            codigos.addAll(roles);
        }

        Set<String> aprobadores = rolRepositorio.findByAprobadorTrueAndActivoTrue()
            .stream()
            .map(Rol::getCodigo)
            .collect(Collectors.toSet());

        for (String codigo : codigos) {
            if (codigo == null || codigo.isBlank()) {
                throw new PoliticaAprobacionInvalidaException(
                    "Una política devolvió un código de rol vacío");
            }
            if (!aprobadores.contains(codigo)) {
                throw new PoliticaAprobacionInvalidaException(
                    "El rol " + codigo
                        + " no existe, no está activo o no es un rol funcional de aprobación");
            }
        }

        return new PlanAprobacion(codigos);
    }
}
