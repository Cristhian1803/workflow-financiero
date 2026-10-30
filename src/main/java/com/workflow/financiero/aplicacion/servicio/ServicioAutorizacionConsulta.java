package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.SolicitudNoEncontradaException;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import org.springframework.stereotype.Service;

/**
 * Reglas de visibilidad de las solicitudes. En el Paso 3 se extiende al
 * aprobador relacionado; en esta etapa solo existen dueño, auditor y
 * administrador.
 */
@Service
public class ServicioAutorizacionConsulta {

    public void verificarListado(UsuarioActual actual) {
        if (esAdministrador(actual) || actual.tieneRol("SOLICITANTE")) {
            return;
        }
        throw new AccesoDenegadoException(
            "No tiene permiso para listar solicitudes");
    }

    public void verificarDetalle(SolicitudFinanciera solicitud, UsuarioActual actual) {
        if (esPropietario(solicitud, actual) || esAdministrador(actual)) {
            return;
        }
        if (!actual.tieneRol("SOLICITANTE")) {
            throw new AccesoDenegadoException(
                "No tiene permiso para consultar la solicitud");
        }
        throw new SolicitudNoEncontradaException();
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

    private boolean esPropietario(SolicitudFinanciera solicitud, UsuarioActual actual) {
        return solicitud.getSolicitante().getId().equals(actual.id());
    }

    private boolean esAdministrador(UsuarioActual actual) {
        return actual.tieneRol("ADMINISTRADOR");
    }
}
