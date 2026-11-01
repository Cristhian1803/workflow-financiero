package com.workflow.financiero.dominio.excepcion;

import com.workflow.financiero.dominio.regla.validacion.ErrorCampo;
import java.util.List;
import lombok.Getter;

/**
 * La solicitud no cumple las reglas de validación. Conserva todos los errores
 * para que las capas superiores los devuelvan en una sola respuesta.
 */
@Getter
public class SolicitudInvalidaException extends RuntimeException {

    private final List<ErrorCampo> errores;

    public SolicitudInvalidaException(List<ErrorCampo> errores) {
        super("La solicitud tiene " + errores.size() + " errores de validación");
        this.errores = List.copyOf(errores);
    }
}
