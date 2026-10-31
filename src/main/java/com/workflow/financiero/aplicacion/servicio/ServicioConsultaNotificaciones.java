package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.modelo.Notificacion;
import com.workflow.financiero.infraestructura.persistencia.NotificacionRepositorio;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Combina las notificaciones dirigidas al usuario y a sus roles activos,
 * eliminando duplicados y ordenando de la más reciente a la más antigua.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioConsultaNotificaciones {

    private final NotificacionRepositorio notificacionRepositorio;

    public List<NotificacionDetalle> listar(UsuarioActual actual) {
        Map<UUID, Notificacion> unicas = new LinkedHashMap<>();

        notificacionRepositorio.buscarPorDestinatarioUsuario(actual.id())
            .forEach(notificacion -> unicas.put(
                notificacion.getId(), notificacion));

        if (!actual.roles().isEmpty()) {
            notificacionRepositorio.buscarPorDestinatarioRol(actual.roles())
                .forEach(notificacion -> unicas.putIfAbsent(
                    notificacion.getId(), notificacion));
        }

        return unicas.values().stream()
            .sorted(Comparator
                .comparing(Notificacion::getCreadoEn)
                .reversed()
                .thenComparing(Notificacion::getId))
            .map(this::detalle)
            .toList();
    }

    private NotificacionDetalle detalle(Notificacion notificacion) {
        if (notificacion.getTarea() == null) {
            return new NotificacionDetalle(
                notificacion.getId(),
                notificacion.getTipo(),
                notificacion.getContenido(),
                notificacion.getEstado(),
                null,
                null,
                null,
                notificacion.getCreadoEn(),
                notificacion.getLeidaEn()
            );
        }

        return new NotificacionDetalle(
            notificacion.getId(),
            notificacion.getTipo(),
            notificacion.getContenido(),
            notificacion.getEstado(),
            notificacion.getTarea().getId(),
            notificacion.getTarea().getRevision().getSolicitud().getId(),
            notificacion.getTarea().getRevision().getNumero(),
            notificacion.getCreadoEn(),
            notificacion.getLeidaEn()
        );
    }
}
