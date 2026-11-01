package com.workflow.financiero.api.controlador;

import com.workflow.financiero.api.contrato.riesgo.NotificacionRiesgoPeticion;
import com.workflow.financiero.api.contrato.riesgo.RecepcionRiesgoRespuesta;
import com.workflow.financiero.api.error.Correlacion;
import com.workflow.financiero.api.error.FiltroFirmaRiesgoExterno;
import com.workflow.financiero.aplicacion.servicio.RegistroEventoExterno;
import com.workflow.financiero.aplicacion.servicio.ServicioEventoExterno;
import com.workflow.financiero.dominio.estado.EstadoEventoExterno;
import com.workflow.financiero.dominio.excepcion.EventoExternoInvalidoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe las notificaciones asíncronas del proveedor de riesgo. La firma se
 * valida en el filtro antes de que el cuerpo llegue aquí.
 */
@RestController
@RequestMapping("/api/v1/integraciones/riesgos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Riesgo externo",
    description = "Notificaciones autenticadas y correlación con el proceso")
public class ControladorRiesgoExterno {

    private final ServicioEventoExterno servicio;

    @PostMapping("/notificacion")
    @Operation(
        summary = "Recibir el resultado de una evaluación externa",
        description = "Valida la firma HMAC, guarda el evento en el inbox y "
            + "lo correlaciona con la revisión y el proceso. Responde 200 si "
            + "se aplicó o era un duplicado, 202 si queda pendiente y 409 si "
            + "el identificador ya existe con otro contenido."
    )
    @ApiResponse(responseCode = "200", description = "Evento procesado u obsoleto")
    @ApiResponse(responseCode = "202", description = "Evento guardado y pendiente")
    @ApiResponse(responseCode = "400", description = "Cuerpo o fecha inválidos")
    @ApiResponse(responseCode = "401", description = "Firma inválida")
    @ApiResponse(responseCode = "409", description = "Conflicto de evento")
    ResponseEntity<RecepcionRiesgoRespuesta> notificar(
        @Valid @RequestBody NotificacionRiesgoPeticion peticion,
        HttpServletRequest peticionHttp
    ) {
        byte[] cuerpo = (byte[]) peticionHttp.getAttribute(
            FiltroFirmaRiesgoExterno.ATRIBUTO_CUERPO);
        if (cuerpo == null) {
            throw new EventoExternoInvalidoException(
                "No se recibió el cuerpo de la notificación");
        }
        validarCabeceras(peticion, peticionHttp);

        RegistroEventoExterno registro = servicio.registrar(
            peticion.aComando(),
            cuerpo,
            Correlacion.obtener(peticionHttp)
        );

        if (!registro.nuevo()) {
            return ResponseEntity.ok(new RecepcionRiesgoRespuesta(
                registro.identificadorEvento(),
                EstadoEventoExterno.DUPLICADO,
                registro.recibidoEn()
            ));
        }

        try {
            EstadoEventoExterno estado = servicio.correlacionar(
                registro.idEvento());
            HttpStatus codigo = estado == EstadoEventoExterno.PENDIENTE
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
            return ResponseEntity.status(codigo)
                .body(new RecepcionRiesgoRespuesta(
                    registro.identificadorEvento(),
                    estado,
                    registro.recibidoEn()
                ));
        } catch (RuntimeException error) {
            log.error(
                "No se pudo correlacionar el evento externo {}: {}",
                registro.identificadorEvento(),
                error.getMessage()
            );
            registrarFallo(registro.idEvento(), error);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RecepcionRiesgoRespuesta(
                    registro.identificadorEvento(),
                    EstadoEventoExterno.PENDIENTE,
                    registro.recibidoEn()
                ));
        }
    }

    private void validarCabeceras(
        NotificacionRiesgoPeticion peticion,
        HttpServletRequest request
    ) {
        String proveedor = request.getHeader(
            FiltroFirmaRiesgoExterno.CABECERA_PROVEEDOR);
        String evento = request.getHeader(
            FiltroFirmaRiesgoExterno.CABECERA_EVENTO);
        if (!peticion.proveedor().equals(proveedor)
            || !peticion.identificadorEvento().equals(evento)) {
            throw new EventoExternoInvalidoException(
                "El cuerpo no coincide con las cabeceras del evento");
        }
    }

    private void registrarFallo(UUID idEvento, RuntimeException error) {
        try {
            servicio.registrarFalloCorrelacion(
                idEvento,
                error.getMessage() == null
                    ? "No se pudo correlacionar el evento"
                    : error.getMessage()
            );
        } catch (RuntimeException fallo) {
            log.error(
                "No se pudo registrar el fallo de correlación del evento {}: {}",
                idEvento,
                fallo.getMessage()
            );
        }
    }
}
