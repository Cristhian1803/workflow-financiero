package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Decide si un actor puede ver y resolver una tarea concreta. Un actor con el
 * rol funcional o con el rol superior configurado puede actuar; una asignación
 * concreta limita la tarea a esa persona.
 */
@Service
@RequiredArgsConstructor
public class ServicioElegibilidadAprobacion {

    private final ServicioElegibilidadAprobador elegibilidadAprobador;

    public ElegibilidadAprobacion evaluar(
        TareaAprobacion tarea,
        UsuarioActual actual
    ) {
        String rolFuncional = tarea.getRol().getCodigo();
        List<String> rolesVisibles = elegibilidadAprobador.rolesVisibles(actual);
        boolean tieneRol = rolesVisibles.contains(rolFuncional);
        boolean delegado = tieneRol && !actual.tieneRol(rolFuncional);

        boolean asignadaAOtro = tarea.getAsignado() != null
            && !tarea.getAsignado().getId().equals(actual.id());
        boolean visible = tarea.getAsignado() == null
            ? tieneRol
            : !asignadaAOtro;

        if (!visible) {
            return new ElegibilidadAprobacion(
                false,
                false,
                false,
                rolFuncional,
                "La tarea no está visible para el actor"
            );
        }

        boolean esSolicitante = tarea.getRevision()
            .getSolicitud()
            .getSolicitante()
            .getId()
            .equals(actual.id());
        if (esSolicitante) {
            return new ElegibilidadAprobacion(
                true,
                false,
                false,
                rolFuncional,
                "El solicitante no puede aprobar su propia solicitud"
            );
        }
        if (!tieneRol) {
            return new ElegibilidadAprobacion(
                true,
                false,
                false,
                rolFuncional,
                "El actor no tiene el rol funcional ni la delegación para esta tarea"
            );
        }

        return new ElegibilidadAprobacion(
            true,
            true,
            delegado,
            rolFuncional,
            null
        );
    }
}
