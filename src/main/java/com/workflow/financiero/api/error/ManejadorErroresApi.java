package com.workflow.financiero.api.error;

import com.workflow.financiero.dominio.excepcion.SolicitudPruebaInvalidaException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErroresApi {

    @ExceptionHandler(SolicitudPruebaInvalidaException.class)
    ResponseEntity<Map<String, String>> solicitudInvalida(SolicitudPruebaInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}
