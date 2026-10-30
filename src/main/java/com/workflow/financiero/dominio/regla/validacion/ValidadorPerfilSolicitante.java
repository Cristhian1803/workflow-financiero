package com.workflow.financiero.dominio.regla.validacion;

/**
 * Valida que el solicitante y su perfil de riesgo existan y estén activos.
 * El perfil llega resuelto desde el usuario persistido.
 */
public class ValidadorPerfilSolicitante implements ValidadorSolicitud {

    @Override
    public ResultadoValidacion validar(ContextoValidacion contexto) {
        if (contexto.solicitante() == null) {
            return ResultadoValidacion.conError("solicitante", "El solicitante no existe");
        }
        if (contexto.perfilSolicitante() == null) {
            return ResultadoValidacion.conError(
                "perfilRiesgo", "El perfil de riesgo del solicitante no existe");
        }
        if (!contexto.perfilSolicitante().isActivo()) {
            return ResultadoValidacion.conError(
                "perfilRiesgo", "El perfil de riesgo del solicitante no está activo");
        }
        return ResultadoValidacion.valido();
    }
}
