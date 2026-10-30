package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.estado.ContextoTransicion;
import com.workflow.financiero.dominio.estado.EstadoRevision;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el rechazo de la revisión actual. La cancelación de las tareas de
 * negocio se completa cuando exista su sincronización.
 */
@Service
@RequiredArgsConstructor
public class ServicioRechazoProceso {

    private final ServicioTransicionRevision servicioTransicion;

    @Transactional
    public void registrar(
        UUID solicitudId,
        UUID revisionId,
        String motivo,
        String correlacion
    ) {
        String detalle = motivo == null || motivo.isBlank()
            ? "Rechazo registrado por el proceso"
            : motivo;

        servicioTransicion.transicionar(
            solicitudId,
            revisionId,
            EstadoRevision.RECHAZADA,
            new ContextoTransicion(null, null, correlacion, detalle)
        );
    }
}
