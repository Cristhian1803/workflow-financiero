package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.List;
import java.util.UUID;

/**
 * Estado del proceso asociado a la revisión actual. Se consulta al motor y se
 * mantiene desacoplado de sus clases concretas.
 */
public record EstadoProceso(
    UUID idSolicitud,
    int revision,
    String identificadorProceso,
    boolean iniciado,
    boolean finalizado,
    EstadoRevision estadoRevision,
    List<String> actividadesActivas,
    List<TareaProceso> tareas
) {

    public EstadoProceso {
        actividadesActivas = List.copyOf(actividadesActivas);
        tareas = List.copyOf(tareas);
    }
}
