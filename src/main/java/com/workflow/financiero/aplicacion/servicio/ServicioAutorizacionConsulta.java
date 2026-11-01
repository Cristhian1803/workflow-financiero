package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.infraestructura.persistencia.TareaAprobacionRepositorio;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reglas de visibilidad de las solicitudes. El dueño, el administrador y el
 * aprobador relacionado con la revisión actual pueden consultar el detalle y
 * el proceso; el historial conserva sus reglas propias.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutorizacionConsulta {

    private final TareaAprobacionRepositorio tareaRepositorio;
    private final ServicioElegibilidadAprobador elegibilidad;

    public void verificarListado(UsuarioActual actual) {
        if (esAdministrador(actual) || actual.tieneRol("SOLICITANTE")) {
            return;
        }
        throw new AccesoDenegadoException(
            "No tiene permiso para listar solicitudes");
    }

    public void verificarDetalle(SolicitudFinanciera solicitud, UsuarioActual actual) {
        verificarConsulta(
            solicitud,
            actual,
            "No tiene permiso para consultar la solicitud");
    }

    public void verificarProceso(SolicitudFinanciera solicitud, UsuarioActual actual) {
        verificarConsulta(
            solicitud,
            actual,
            "No tiene permiso para consultar el proceso");
    }

    public void verificarHistorial(SolicitudFinanciera solicitud, UsuarioActual actual) {
        if (esPropietario(solicitud, actual)
            || esAdministrador(actual)
            || actual.tieneRol("AUDITOR")) {
            return;
        }
        if (!actual.tieneRol("SOLICITANTE")) {
            throw new AccesoDenegadoException(
                "No tiene permiso para consultar el historial");
        }
        throw new SolicitudNoEncontradaException();
    }

    private void verificarConsulta(
        SolicitudFinanciera solicitud,
        UsuarioActual actual,
        String mensajeAcceso
    ) {
        if (esPropietario(solicitud, actual) || esAdministrador(actual)) {
            return;
        }
        if (tieneTareaEnRevisionActual(solicitud, actual)) {
            return;
        }
        if (!actual.tieneRol("SOLICITANTE")) {
            throw new AccesoDenegadoException(mensajeAcceso);
        }
        throw new SolicitudNoEncontradaException();
    }

    private boolean tieneTareaEnRevisionActual(
        SolicitudFinanciera solicitud,
        UsuarioActual actual
    ) {
        RevisionSolicitud revision = solicitud.getRevisionActual();
        if (revision == null) {
            return false;
        }
        if (tareaRepositorio.existeTareaAsignada(revision.getId(), actual.id())) {
            return true;
        }

        List<String> roles = elegibilidad.rolesVisibles(actual);
        return !roles.isEmpty()
            && tareaRepositorio.existeTareaPorRoles(revision.getId(), roles);
    }

    private boolean esPropietario(SolicitudFinanciera solicitud, UsuarioActual actual) {
        return solicitud.getSolicitante().getId().equals(actual.id());
    }

    private boolean esAdministrador(UsuarioActual actual) {
        return actual.tieneRol("ADMINISTRADOR");
    }
}
