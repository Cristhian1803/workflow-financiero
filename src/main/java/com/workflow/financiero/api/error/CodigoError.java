package com.workflow.financiero.api.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Códigos estables del contrato de errores de la API.
 */
@Getter
public enum CodigoError {

    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "No autenticado"),
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "Acceso denegado"),
    DATOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Datos inválidos"),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST, "JSON inválido"),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "Solicitud inválida"),
    SOLICITUD_PRUEBA_INVALIDA(HttpStatus.BAD_REQUEST, "Solicitud de prueba inválida"),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso no encontrado"),
    TAREA_NO_ENCONTRADA(HttpStatus.NOT_FOUND, "Tarea no encontrada"),
    TAREA_YA_RESUELTA(HttpStatus.CONFLICT, "Tarea ya resuelta"),
    TRANSICION_TAREA_INVALIDA(HttpStatus.CONFLICT, "Transición de tarea inválida"),
    APROBACION_NO_PERMITIDA(HttpStatus.FORBIDDEN, "Aprobación no permitida"),
    SEGREGACION_FUNCIONES(HttpStatus.FORBIDDEN, "Segregación de funciones"),
    CONFLICTO_DATOS(HttpStatus.CONFLICT, "Conflicto de datos"),
    REENVIO_NO_PERMITIDO(HttpStatus.UNPROCESSABLE_ENTITY, "Reenvío no permitido"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }
}
