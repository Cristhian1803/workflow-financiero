package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.RolAprobadorInvalidoException;
import com.workflow.financiero.dominio.excepcion.SegregacionFuncionesException;
import com.workflow.financiero.dominio.modelo.RegistroActorRol;
import com.workflow.financiero.dominio.modelo.RegistroActorRolId;
import com.workflow.financiero.dominio.modelo.Rol;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.RegistroActorRolRepositorio;
import com.workflow.financiero.infraestructura.persistencia.RolRepositorio;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el rol funcional que un actor aprobó en una solicitud. La misma
 * persona puede repetir ese rol en otra revisión, pero no puede aprobar otro
 * rol funcional distinto dentro de la misma solicitud.
 */
@Service
@RequiredArgsConstructor
public class RegistroActorRolServicio {

    private final RegistroActorRolRepositorio registroRepositorio;
    private final RolRepositorio rolRepositorio;

    @Transactional
    public RegistroActorRol registrarAprobacion(UUID solicitudId, UUID usuarioId, String rolCodigo) {
        if (rolCodigo == null || rolCodigo.isBlank()) {
            throw new RolAprobadorInvalidoException("El rol funcional es obligatorio");
        }

        Rol rol = rolRepositorio.findById(rolCodigo)
            .filter(Rol::isAprobador)
            .filter(Rol::isActivo)
            .orElseThrow(() -> new RolAprobadorInvalidoException(
                "El rol " + rolCodigo + " no es un rol funcional de aprobación activo"));

        registroRepositorio.insertarSiNoExiste(
            solicitudId,
            usuarioId,
            rol.getCodigo(),
            Instantes.ahora()
        );

        RegistroActorRol registro = registroRepositorio
            .findById(new RegistroActorRolId(solicitudId, usuarioId))
            .orElseThrow(() -> new IllegalStateException(
                "No se pudo recuperar el registro del actor para la solicitud"));

        if (!registro.getRol().getCodigo().equals(rol.getCodigo())) {
            throw new SegregacionFuncionesException(
                "El actor ya aprobó el rol " + registro.getRol().getCodigo()
                    + " en esta solicitud; no puede aprobar " + rol.getCodigo());
        }

        return registro;
    }
}
