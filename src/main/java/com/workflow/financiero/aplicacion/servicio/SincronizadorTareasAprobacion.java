package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesAprobacion;
import com.workflow.financiero.aplicacion.puerto.PuertoProceso;
import com.workflow.financiero.aplicacion.puerto.TareaMotor;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.modelo.TareaAprobacion;
import com.workflow.financiero.infraestructura.persistencia.RevisionSolicitudRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copia las tareas humanas del motor a la tabla de negocio. La operación es
 * idempotente: el mismo identificador de motor no se asocia dos veces y el
 * vencimiento ya programado no se mueve.
 */
@Service
@RequiredArgsConstructor
public class SincronizadorTareasAprobacion {

    private static final Map<String, String> ROLES_POR_DEFINICION = Map.of(
        "tareaSupervisor", "SUPERVISOR",
        "tareaGerente", "GERENTE",
        "tareaRiesgos", "RIESGOS"
    );

    private final RevisionSolicitudRepositorio revisionRepositorio;
    private final TareaAprobacionRepositorio tareaRepositorio;
    private final RolRepositorio rolRepositorio;
    private final PuertoProceso puertoProceso;
    private final PropiedadesAprobacion propiedadesAprobacion;

    @Transactional
    public void sincronizar(
        UUID solicitudId,
        UUID revisionId,
        String identificadorProceso
    ) {
        if (solicitudId == null || revisionId == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias para sincronizar tareas");
        }
        if (identificadorProceso == null || identificadorProceso.isBlank()) {
            throw new IllegalArgumentException(
                "El identificador de proceso es obligatorio para sincronizar tareas");
        }

        RevisionSolicitud revision = revisionRepositorio
            .buscarDetallePorId(revisionId)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la revisión " + revisionId));

        if (!revision.getSolicitud().getId().equals(solicitudId)) {
            throw new IllegalStateException(
                "La revisión no pertenece a la solicitud indicada");
        }
        if (revision.getSolicitud().getRevisionActual() == null
            || !revision.getSolicitud().getRevisionActual().getId().equals(revisionId)) {
            throw new IllegalStateException(
                "La revisión indicada no es la actual de la solicitud");
        }
        if (!identificadorProceso.equals(revision.getIdentificadorProceso())) {
            throw new IllegalStateException(
                "La revisión no tiene asociado el proceso indicado");
        }

        Map<String, TareaAprobacion> existentes = tareaRepositorio
            .buscarPorRevision(revisionId)
            .stream()
            .collect(Collectors.toMap(
                tarea -> tarea.getRol().getCodigo(),
                Function.identity()
            ));

        List<TareaAprobacion> tareas = new ArrayList<>();
        for (TareaMotor tareaMotor : puertoProceso.tareasActivas(identificadorProceso)) {
            String rolCodigo = ROLES_POR_DEFINICION.get(tareaMotor.definicion());
            if (rolCodigo == null) {
                throw new IllegalStateException(
                    "No hay un rol de negocio para la tarea "
                        + tareaMotor.definicion());
            }

            TareaAprobacion tarea = existentes.get(rolCodigo);
            if (tarea == null) {
                Rol rol = rolRepositorio.findById(rolCodigo)
                    .orElseThrow(() -> new NoSuchElementException(
                        "No existe el rol " + rolCodigo));
                tarea = new TareaAprobacion(revision, rol);
                tarea.asignarIdentificadorMotor(tareaMotor.id());
                tarea.programarVencimiento(
                    tarea.getCreadoEn().plus(propiedadesAprobacion.reasignacion()));
            } else {
                asociarIdentificador(tarea, tareaMotor.id());
                programarVencimientoSiFalta(tarea);
            }
            tareas.add(tarea);
        }

        if (!tareas.isEmpty()) {
            tareaRepositorio.saveAllAndFlush(tareas);
        }
    }

    private void asociarIdentificador(TareaAprobacion tarea, String identificador) {
        if (tarea.getIdentificadorTareaMotor() == null) {
            tarea.asignarIdentificadorMotor(identificador);
            return;
        }
        if (!tarea.getIdentificadorTareaMotor().equals(identificador)) {
            throw new IllegalStateException(
                "La tarea de " + tarea.getRol().getCodigo()
                    + " ya está asociada a otro identificador de motor");
        }
    }

    private void programarVencimientoSiFalta(TareaAprobacion tarea) {
        if (tarea.getFechaVencimiento() == null) {
            tarea.programarVencimiento(
                tarea.getCreadoEn().plus(propiedadesAprobacion.reasignacion()));
        }
    }
}
