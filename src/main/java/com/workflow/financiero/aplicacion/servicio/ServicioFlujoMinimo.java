package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.dominio.excepcion.ErrorSimuladoPrueba;
import com.workflow.financiero.dominio.excepcion.SolicitudPruebaInvalidaException;
import com.workflow.financiero.dominio.modelo.EventoSolicitudPrueba;
import com.workflow.financiero.dominio.modelo.SolicitudPrueba;
import com.workflow.financiero.infraestructura.persistencia.EventoSolicitudPruebaRepositorio;
import com.workflow.financiero.infraestructura.persistencia.SolicitudPruebaRepositorio;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la solicitud, inicia el proceso y guarda el resultado simulado.
 */
@Service
@RequiredArgsConstructor
public class ServicioFlujoMinimo {

    public static final String CLAVE_PROCESO_FLUJO_MINIMO = "procesoFlujoMinimo";
    public static final String ESTADO_RECIBIDA = "RECIBIDA";
    public static final String ESTADO_EJECUTADA_SIMULADA = "EJECUTADA_SIMULADA";

    private final SolicitudPruebaRepositorio solicitudRepositorio;
    private final EventoSolicitudPruebaRepositorio eventoRepositorio;
    private final RuntimeService runtimeService;

    @Transactional
    public UUID crearSolicitudYProceso(long monto, String moneda, boolean fallarDespues) {
        validarEntrada(monto, moneda);

        SolicitudPrueba solicitud = new SolicitudPrueba();
        UUID id = UUID.randomUUID();
        solicitud.setId(id);
        solicitud.setEstado(ESTADO_RECIBIDA);
        solicitud.setMonto(monto);
        solicitud.setMoneda(moneda);
        solicitudRepositorio.save(solicitud);

        registrarEvento(id, "SOLICITUD_CREADA", "Solicitud registrada");

        Map<String, Object> variables = new HashMap<>();
        variables.put("solicitudId", id.toString());
        ProcessInstance instancia = runtimeService.startProcessInstanceByKey(CLAVE_PROCESO_FLUJO_MINIMO, variables);
        SolicitudPrueba actualizada = solicitudRepositorio.findById(id).orElseThrow();
        actualizada.setIdentificadorInstanciaProceso(instancia.getId());
        solicitudRepositorio.save(actualizada);

        if (fallarDespues) {
            throw new ErrorSimuladoPrueba();
        }
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SolicitudPrueba> buscar(UUID id) {
        return solicitudRepositorio.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EventoSolicitudPrueba> historial(UUID solicitudId) {
        return eventoRepositorio.findBySolicitudIdOrderByCreadoEnAsc(solicitudId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long contarInstanciasActivasPorSolicitud(UUID solicitudId) {
        return runtimeService.createProcessInstanceQuery()
            .variableValueEquals("solicitudId", solicitudId.toString())
            .active()
            .count();
    }

    private void validarEntrada(long monto, String moneda) {
        if (monto <= 0) {
            throw new SolicitudPruebaInvalidaException("El monto debe ser mayor que cero");
        }
        if (!"PYG".equals(moneda)) {
            throw new SolicitudPruebaInvalidaException("La moneda debe ser Gs.");
        }
    }

    private void registrarEvento(UUID solicitudId, String tipo, String detalle) {
        EventoSolicitudPrueba evento = new EventoSolicitudPrueba();
        evento.setId(UUID.randomUUID());
        evento.setSolicitudId(solicitudId);
        evento.setTipo(tipo);
        evento.setDetalle(detalle);
        eventoRepositorio.save(evento);
    }
}
