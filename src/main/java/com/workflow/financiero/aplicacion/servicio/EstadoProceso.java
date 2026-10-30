package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.List;
import java.util.UUID;

/**
 * Estado del proceso asociado a la revisión actual. En el Paso 2 todavía no
 * existe una instancia de Flowable.
 */
public record EstadoProceso(
    UUID idSolicitud,
    int revision,
    String identificadorProceso,
    boolean iniciado,
    boolean finalizado,
    EstadoRevision estadoRevision,
    List<TareaProceso> tareas
) {

    public EstadoProceso {
        tareas = List.copyOf(tareas);
    }
}
