package com.workflow.financiero.aplicacion.configuracion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plazos y responsables superiores de las tareas de aprobación. Las
 * duraciones se usan en los temporizadores y en las fechas visibles; el mapa
 * de superiores define qué rol puede actuar por delegación.
 */
@ConfigurationProperties(prefix = "flujo.aprobacion")
public record PropiedadesAprobacion(
    Duration notificacion,
    Duration reasignacion,
    Map<String, String> superiores
) {

    private static final List<String> ROLES_FUNCIONALES = List.of(
        "SUPERVISOR",
        "GERENTE",
        "RIESGOS"
    );

    public PropiedadesAprobacion {
        if (notificacion == null || notificacion.isNegative()) {
            throw new IllegalStateException(
                "La duración de notificación debe ser cero o mayor");
        }
        if (reasignacion == null || reasignacion.isNegative()) {
            throw new IllegalStateException(
                "La duración de reasignación debe ser cero o mayor");
        }
        if (superiores == null || superiores.isEmpty()) {
            throw new IllegalStateException(
                "El mapa de superiores de aprobación no puede estar vacío");
        }

        superiores = Map.copyOf(superiores);
        for (String rol : ROLES_FUNCIONALES) {
            String superior = superiores.get(rol);
            if (superior == null || superior.isBlank()) {
                throw new IllegalStateException(
                    "Falta el superior configurado para el rol " + rol);
            }
        }
    }
}
