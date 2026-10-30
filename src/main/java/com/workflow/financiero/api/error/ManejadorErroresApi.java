package com.workflow.financiero.api.error;

import com.workflow.financiero.aplicacion.excepcion.CredencialesInvalidasException;
import com.workflow.financiero.dominio.excepcion.AccesoDenegadoException;
import com.workflow.financiero.dominio.excepcion.SolicitudInvalidaException;
import com.workflow.financiero.dominio.excepcion.SolicitudPruebaInvalidaException;
import com.workflow.financiero.dominio.excepcion.TransicionEstadoInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones de la API al contrato uniforme de errores.
 */
@RestControllerAdvice
@Slf4j
public class ManejadorErroresApi {

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<ProblemDetail> credencialesInvalidas(
        CredencialesInvalidasException excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.CREDENCIALES_INVALIDAS,
            "Usuario o clave inválidos",
            peticion
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> datosInvalidos(
        MethodArgumentNotValidException excepcion,
        HttpServletRequest peticion
    ) {
        List<ErrorCampoRespuesta> errores = excepcion.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ErrorCampoRespuesta(
                error.getField(),
                error.getDefaultMessage() == null
                    ? "Valor inválido"
                    : error.getDefaultMessage()))
            .sorted(Comparator.comparing(ErrorCampoRespuesta::campo))
            .toList();

        ProblemDetail problema = ProblemaApi.crear(
            CodigoError.DATOS_INVALIDOS,
            "La petición contiene datos inválidos",
            peticion
        );
        ProblemaApi.conErrores(problema, errores);
        return respuesta(problema, CodigoError.DATOS_INVALIDOS.getEstado());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> jsonInvalido(
        HttpMessageNotReadableException excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.JSON_INVALIDO,
            "El cuerpo de la petición no es un JSON válido",
            peticion
        );
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<ProblemDetail> solicitudInvalida(
        SolicitudInvalidaException excepcion,
        HttpServletRequest peticion
    ) {
        List<ErrorCampoRespuesta> errores = excepcion.getErrores().stream()
            .map(error -> new ErrorCampoRespuesta(error.campo(), error.mensaje()))
            .sorted(Comparator.comparing(ErrorCampoRespuesta::campo))
            .toList();

        ProblemDetail problema = ProblemaApi.crear(
            CodigoError.SOLICITUD_INVALIDA,
            "La solicitud contiene datos inválidos",
            peticion
        );
        ProblemaApi.conErrores(problema, errores);
        return respuesta(problema, CodigoError.SOLICITUD_INVALIDA.getEstado());
    }

    @ExceptionHandler(SolicitudPruebaInvalidaException.class)
    ResponseEntity<ProblemDetail> solicitudPruebaInvalida(
        SolicitudPruebaInvalidaException excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.SOLICITUD_PRUEBA_INVALIDA,
            "La solicitud de prueba contiene datos inválidos",
            peticion
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> parametroInvalido(
        MethodArgumentTypeMismatchException excepcion,
        HttpServletRequest peticion
    ) {
        String campo = excepcion.getPropertyName() == null
            ? "parametro"
            : excepcion.getPropertyName();
        ProblemDetail problema = ProblemaApi.crear(
            CodigoError.DATOS_INVALIDOS,
            "La petición contiene datos inválidos",
            peticion
        );
        ProblemaApi.conErrores(
            problema,
            List.of(new ErrorCampoRespuesta(campo, "Valor inválido"))
        );
        return respuesta(problema, CodigoError.DATOS_INVALIDOS.getEstado());
    }

    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    ResponseEntity<ProblemDetail> recursoNoEncontrado(
        Exception excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.RECURSO_NO_ENCONTRADO,
            "No se encontró el recurso solicitado",
            peticion
        );
    }

    @ExceptionHandler({
        DataIntegrityViolationException.class,
        TransicionEstadoInvalidaException.class,
        ObjectOptimisticLockingFailureException.class
    })
    ResponseEntity<ProblemDetail> conflictoDeDatos(
        Exception excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.CONFLICTO_DATOS,
            "Los datos entran en conflicto con el estado actual",
            peticion
        );
    }

    @ExceptionHandler({AccessDeniedException.class, AccesoDenegadoException.class})
    ResponseEntity<ProblemDetail> accesoDenegado(
        RuntimeException excepcion,
        HttpServletRequest peticion
    ) {
        return respuesta(
            CodigoError.ACCESO_DENEGADO,
            "No tiene permiso para realizar esta operación",
            peticion
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> errorInesperado(
        Exception excepcion,
        HttpServletRequest peticion
    ) {
        log.error(
            "Error no controlado [correlación={}]",
            Correlacion.obtener(peticion),
            excepcion
        );
        return respuesta(
            CodigoError.ERROR_INTERNO,
            "Ocurrió un error inesperado",
            peticion
        );
    }

    private ResponseEntity<ProblemDetail> respuesta(
        CodigoError codigo,
        String detalle,
        HttpServletRequest peticion
    ) {
        return respuesta(
            ProblemaApi.crear(codigo, detalle, peticion),
            codigo.getEstado()
        );
    }

    private ResponseEntity<ProblemDetail> respuesta(
        ProblemDetail problema,
        HttpStatus estado
    ) {
        return ResponseEntity.status(estado)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problema);
    }
}
