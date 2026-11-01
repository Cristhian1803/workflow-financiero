package com.workflow.financiero.aplicacion.servicio;

import com.workflow.financiero.aplicacion.configuracion.PropiedadesEjecucion;
import com.workflow.financiero.aplicacion.configuracion.PropiedadesRiesgoExterno;
import com.workflow.financiero.dominio.estado.EstadoEvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.EvaluacionRiesgo;
import com.workflow.financiero.dominio.modelo.RevisionSolicitud;
import com.workflow.financiero.dominio.modelo.SolicitudFinanciera;
import com.workflow.financiero.dominio.regla.riesgo.OrigenRiesgo;
import com.workflow.financiero.dominio.riesgo.ClavesRiesgoExterno;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.persistencia.EvaluacionRiesgoRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea, reclama y cierra la intención de evaluación externa. La llamada al
 * proveedor ocurre fuera de estas transacciones.
 */
@Service
@RequiredArgsConstructor
public class ServicioSolicitudRiesgoExterno {

    private static final List<EstadoEvaluacionRiesgo> ESTADOS_VIGENTES =
        List.of(
            EstadoEvaluacionRiesgo.PENDIENTE,
            EstadoEvaluacionRiesgo.RECIBIDA
        );

    private final EvaluacionRiesgoRepositorio evaluacionRepositorio;
    private final PropiedadesRiesgoExterno propiedades;
    private final PropiedadesEjecucion propiedadesEjecucion;
    private final ServicioIncidente servicioIncidente;

    @Transactional
    public EvaluacionRiesgo registrarIntencion(
        SolicitudFinanciera solicitud,
        RevisionSolicitud revision,
        String correlacion
    ) {
        if (solicitud == null || revision == null) {
            throw new IllegalArgumentException(
                "La solicitud y la revisión son obligatorias");
        }
        if (!revision.getSolicitud().getId().equals(solicitud.getId())) {
            throw new IllegalArgumentException(
                "La revisión no pertenece a la solicitud indicada");
        }

        Optional<EvaluacionRiesgo> existente =
            evaluacionRepositorio.buscarVigentePorRevision(
                revision.getId(),
                OrigenRiesgo.EXTERNO,
                ESTADOS_VIGENTES
            );
        if (existente.isPresent()) {
            return existente.get();
        }

        EvaluacionRiesgo evaluacion = new EvaluacionRiesgo(
            revision,
            OrigenRiesgo.EXTERNO,
            EstadoEvaluacionRiesgo.PENDIENTE,
            ClavesRiesgoExterno.solicitud(
                solicitud.getId(), revision.getId()),
            correlacion,
            propiedades.proveedor()
        );
        return evaluacionRepositorio.saveAndFlush(evaluacion);
    }

    @Transactional
    public Optional<SolicitudEvaluacionRiesgo> reclamarSiguiente(
        String trabajador
    ) {
        OffsetDateTime ahora = OffsetDateTime.ofInstant(
            Instantes.ahora(), ZoneOffset.UTC);
        OffsetDateTime limite = ahora.minus(
            propiedadesEjecucion.reclamacionVencida());

        Optional<EvaluacionRiesgo> reclamada =
            evaluacionRepositorio.reclamarSiguiente(
                limite,
                ahora,
                propiedades.maximoIntentos()
            );
        if (reclamada.isEmpty()) {
            return Optional.empty();
        }

        EvaluacionRiesgo evaluacion = reclamada.get();
        evaluacion.reclamar(trabajador);
        evaluacionRepositorio.flush();

        RevisionSolicitud revision = evaluacion.getRevision();
        return Optional.of(new SolicitudEvaluacionRiesgo(
            evaluacion.getId(),
            revision.getSolicitud().getId(),
            revision.getId(),
            revision.getNumero(),
            revision.getNivelRiesgo(),
            revision.getMonto(),
            revision.getMoneda().getCodigo(),
            revision.getTipoOperacion().getCodigo(),
            evaluacion.getClave()
        ));
    }

    @Transactional
    public void registrarEnvio(UUID idEvaluacion, String referencia) {
        EvaluacionRiesgo evaluacion = bloquear(idEvaluacion);
        evaluacion.registrarEnvio(referencia);
        evaluacionRepositorio.flush();
    }

    @Transactional
    public void registrarFallo(UUID idEvaluacion, String error) {
        EvaluacionRiesgo evaluacion = bloquear(idEvaluacion);
        evaluacion.registrarFallo(
            error,
            Instantes.ahora().plus(propiedades.reintento()),
            propiedades.maximoIntentos()
        );
        evaluacionRepositorio.flush();
        if (evaluacion.getProximoIntentoEn() == null
            && evaluacion.estaPendiente()) {
            servicioIncidente.crearRiesgoExterno(
                evaluacion.getRevision().getSolicitud(),
                evaluacion.getRevision(),
                "Se agotaron los reintentos de envío de la evaluación externa",
                evaluacion.getCorrelacion()
            );
        }
    }

    private EvaluacionRiesgo bloquear(UUID idEvaluacion) {
        return evaluacionRepositorio.buscarPorIdParaActualizar(idEvaluacion)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la evaluación " + idEvaluacion));
    }
}
