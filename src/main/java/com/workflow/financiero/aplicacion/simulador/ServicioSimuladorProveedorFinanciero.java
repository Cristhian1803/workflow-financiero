package com.workflow.financiero.aplicacion.simulador;

import com.workflow.financiero.aplicacion.excepcion.ConflictoProveedorFinancieroException;
import com.workflow.financiero.dominio.ejecucion.AccionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ClavesOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.EstadoOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.HuellaOperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.OperacionFinanciera;
import com.workflow.financiero.dominio.ejecucion.ResultadoOperacionFinanciera;
import com.workflow.financiero.dominio.excepcion.DecisionInvalidaException;
import com.workflow.financiero.dominio.temporal.Instantes;
import com.workflow.financiero.infraestructura.simulador.OperacionSimulada;
import com.workflow.financiero.infraestructura.simulador.OperacionSimuladaRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proveedor financiero de demostración con estado persistido. Las claves y
 * huellas se validan contra la identidad de la operación; una clave repetida
 * devuelve el mismo resultado y una huella distinta produce conflicto.
 */
@Service
@Profile({"desarrollo", "pruebas"})
@RequiredArgsConstructor
public class ServicioSimuladorProveedorFinanciero {

    private final OperacionSimuladaRepositorio repositorio;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ResultadoOperacionFinanciera reservar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.RESERVA);
        validarIdentidad(operacion);

        insertarReserva(operacion);
        OperacionSimulada existente = repositorio
            .findByClave(operacion.clave())
            .orElse(null);
        if (existente == null) {
            throw new ConflictoProveedorFinancieroException(
                "Ya existe una reserva para la revisión indicada");
        }
        validarHuella(existente, operacion);
        return resultado(existente);
    }

    @Transactional
    public ResultadoOperacionFinanciera confirmar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.CONFIRMACION);
        validarIdentidad(operacion);

        OperacionSimulada existente = repositorio
            .findByClave(operacion.clave())
            .orElse(null);
        if (existente != null) {
            validarHuella(existente, operacion);
            return resultado(existente);
        }

        OperacionSimulada reserva = reservaBloqueada(operacion);
        existente = repositorio.findByClave(operacion.clave()).orElse(null);
        if (existente != null) {
            validarHuella(existente, operacion);
            return resultado(existente);
        }
        validarMismaIntencion(reserva, operacion);
        if (reserva.getEstado() != EstadoOperacionFinanciera.RESERVADA) {
            throw new ConflictoProveedorFinancieroException(
                "La reserva no está en estado RESERVADA");
        }
        if (repositorio.findBySolicitudIdAndRevisionIdAndAccion(
                operacion.solicitudId(),
                operacion.revisionId(),
                AccionFinanciera.CONFIRMACION
            ).isPresent()) {
            throw new ConflictoProveedorFinancieroException(
                "La revisión ya tiene otra confirmación");
        }

        reserva.confirmar();
        OperacionSimulada confirmacion = nuevaOperacion(
            operacion,
            AccionFinanciera.CONFIRMACION,
            EstadoOperacionFinanciera.CONFIRMADA
        );
        repositorio.saveAndFlush(confirmacion);
        return resultado(confirmacion);
    }

    @Transactional
    public ResultadoOperacionFinanciera liberar(OperacionFinanciera operacion) {
        exigirAccion(operacion, AccionFinanciera.LIBERACION);
        validarIdentidad(operacion);

        OperacionSimulada existente = repositorio
            .findByClave(operacion.clave())
            .orElse(null);
        if (existente != null) {
            validarHuella(existente, operacion);
            return resultado(existente);
        }

        OperacionSimulada reserva = reservaBloqueada(operacion);
        existente = repositorio.findByClave(operacion.clave()).orElse(null);
        if (existente != null) {
            validarHuella(existente, operacion);
            return resultado(existente);
        }
        validarMismaIntencion(reserva, operacion);
        if (reserva.getEstado() == EstadoOperacionFinanciera.CONFIRMADA) {
            throw new ConflictoProveedorFinancieroException(
                "La reserva ya fue confirmada; la ejecución es irreversible");
        }
        if (reserva.getEstado() != EstadoOperacionFinanciera.RESERVADA) {
            throw new ConflictoProveedorFinancieroException(
                "La reserva no está en estado RESERVADA");
        }
        if (repositorio.findBySolicitudIdAndRevisionIdAndAccion(
                operacion.solicitudId(),
                operacion.revisionId(),
                AccionFinanciera.LIBERACION
            ).isPresent()) {
            throw new ConflictoProveedorFinancieroException(
                "La revisión ya tiene otra liberación");
        }

        reserva.liberar();
        OperacionSimulada liberacion = nuevaOperacion(
            operacion,
            AccionFinanciera.LIBERACION,
            EstadoOperacionFinanciera.LIBERADA
        );
        repositorio.saveAndFlush(liberacion);
        return resultado(liberacion);
    }

    @Transactional(readOnly = true)
    public ResultadoOperacionFinanciera consultar(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new DecisionInvalidaException(
                "clave", "La clave de la operación es obligatoria");
        }
        return repositorio.findByClave(clave)
            .map(this::resultado)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe la operación " + clave));
    }

    private void insertarReserva(OperacionFinanciera operacion) {
        jdbcTemplate.update(
            """
            INSERT INTO operacion_simulada (
                opersimu_id, opersimu_clave, opersimu_huella, opersimu_accion,
                opersimu_estado, opersimu_referencia, opersimu_solifina_id,
                opersimu_revisoli_id, opersimu_monto, opersimu_mone_codigo,
                opersimu_tipooper_codigo, opersimu_version, opersimu_creado_en,
                opersimu_actualizado_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, NULL)
            ON CONFLICT DO NOTHING
            """,
            UUID.randomUUID(),
            operacion.clave(),
            operacion.huella(),
            operacion.accion().name(),
            EstadoOperacionFinanciera.RESERVADA.name(),
            generarReferencia(AccionFinanciera.RESERVA),
            operacion.solicitudId(),
            operacion.revisionId(),
            operacion.monto(),
            operacion.moneda(),
            operacion.tipoOperacion(),
            OffsetDateTime.ofInstant(Instantes.ahora(), ZoneOffset.UTC)
        );
    }

    private OperacionSimulada reservaBloqueada(OperacionFinanciera operacion) {
        String claveReserva = ClavesOperacionFinanciera.reserva(
            operacion.solicitudId(), operacion.revisionId());
        return repositorio.buscarPorClaveParaActualizar(claveReserva)
            .orElseThrow(() -> new ConflictoProveedorFinancieroException(
                "No existe una reserva para la revisión indicada"));
    }

    private OperacionSimulada nuevaOperacion(
        OperacionFinanciera operacion,
        AccionFinanciera accion,
        EstadoOperacionFinanciera estado
    ) {
        return new OperacionSimulada(
            UUID.randomUUID(),
            operacion.clave(),
            operacion.huella(),
            accion,
            estado,
            generarReferencia(accion),
            operacion.solicitudId(),
            operacion.revisionId(),
            operacion.monto(),
            operacion.moneda(),
            operacion.tipoOperacion(),
            Instantes.ahora()
        );
    }

    private void exigirAccion(
        OperacionFinanciera operacion,
        AccionFinanciera esperada
    ) {
        if (operacion.accion() != esperada) {
            throw new DecisionInvalidaException(
                "accion",
                "La acción no corresponde a la operación solicitada"
            );
        }
    }

    private void validarIdentidad(OperacionFinanciera operacion) {
        String claveEsperada = ClavesOperacionFinanciera.clave(
            operacion.accion(),
            operacion.solicitudId(),
            operacion.revisionId()
        );
        if (!claveEsperada.equals(operacion.clave())) {
            throw new DecisionInvalidaException(
                "clave",
                "La clave no corresponde a la identidad de la operación"
            );
        }
        String huellaEsperada = HuellaOperacionFinanciera.calcular(operacion);
        if (!huellaEsperada.equals(operacion.huella())) {
            throw new DecisionInvalidaException(
                "huella",
                "La huella no corresponde a los parámetros de la operación"
            );
        }
    }

    private void validarHuella(
        OperacionSimulada existente,
        OperacionFinanciera operacion
    ) {
        if (!existente.getHuella().equals(operacion.huella())) {
            throw new ConflictoProveedorFinancieroException(
                "La clave ya fue utilizada con otros parámetros");
        }
    }

    private void validarMismaIntencion(
        OperacionSimulada reserva,
        OperacionFinanciera operacion
    ) {
        boolean mismaIntencion = reserva.getSolicitudId().equals(
                operacion.solicitudId())
            && reserva.getRevisionId().equals(operacion.revisionId())
            && reserva.getMonto().compareTo(operacion.monto()) == 0
            && reserva.getMoneda().equals(operacion.moneda())
            && reserva.getTipoOperacion().equals(operacion.tipoOperacion());
        if (!mismaIntencion) {
            throw new ConflictoProveedorFinancieroException(
                "La operación no coincide con la reserva original");
        }
    }

    private String generarReferencia(AccionFinanciera accion) {
        String prefijo = switch (accion) {
            case RESERVA -> "RES-";
            case CONFIRMACION -> "CONF-";
            case LIBERACION -> "LIB-";
        };
        return prefijo + UUID.randomUUID();
    }

    private ResultadoOperacionFinanciera resultado(OperacionSimulada operacion) {
        return new ResultadoOperacionFinanciera(
            operacion.getId(),
            operacion.getClave(),
            operacion.getAccion(),
            operacion.getEstado(),
            operacion.getReferencia(),
            operacion.getHuella(),
            operacion.getActualizadoEn() == null
                ? operacion.getCreadoEn()
                : operacion.getActualizadoEn()
        );
    }
}
